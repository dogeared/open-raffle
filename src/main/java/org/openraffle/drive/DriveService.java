package org.openraffle.drive;

import org.openraffle.domain.DriveConnection;
import org.openraffle.repository.DriveConnectionRepository;
import org.openraffle.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The app's Google Drive connection: connect (OAuth consent → a folder the app creates in
 * the account's Drive), check, reconnect, disconnect, and move picture bytes in and out.
 * Any failure is recorded on the connection so the UI can say what is wrong; uploads are
 * refused while it is unhealthy and the app falls back to BoardGameGeek pictures.
 */
@Service
@Transactional
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);
    public static final String FOLDER_NAME = "Open Raffle prize pictures";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DriveConnectionRepository connections;
    private final DriveClient drive;
    private final CurrentUser currentUser;

    /** The current short-lived access token, fetched from the refresh token when needed. */
    private volatile String accessToken;
    private volatile Instant accessTokenUntil = Instant.EPOCH;

    public DriveService(DriveConnectionRepository connections, DriveClient drive, CurrentUser currentUser) {
        this.connections = connections;
        this.drive = drive;
        this.currentUser = currentUser;
    }

    public enum State { NOT_CONFIGURED, NOT_CONNECTED, CONNECTED, UNHEALTHY }

    public record Status(State state, DriveConnection connection) {
        public boolean canUpload() {
            return state == State.CONNECTED;
        }
    }

    @Transactional(readOnly = true)
    public Status status() {
        if (!drive.isConfigured()) {
            return new Status(State.NOT_CONFIGURED, null);
        }
        return connection()
                .map(c -> new Status(c.isHealthy() ? State.CONNECTED : State.UNHEALTHY, c))
                .orElse(new Status(State.NOT_CONNECTED, null));
    }

    /** Whether uploads are possible right now. */
    @Transactional(readOnly = true)
    public boolean canUpload() {
        return status().canUpload();
    }

    @Transactional(readOnly = true)
    public Optional<DriveConnection> connection() {
        return connections.findById(DriveConnection.SINGLETON_ID);
    }

    /** A fresh random state for the consent round trip, to be kept in the user's session. */
    public static String newState() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String authorizationUrl(String redirectUri, String state) {
        return drive.authorizationUrl(redirectUri, state);
    }

    /**
     * Finishes the consent flow: swaps the code for tokens, creates (or re-creates) the
     * app's folder in that account and stores the connection, replacing any earlier one.
     */
    public DriveConnection complete(String code, String redirectUri) throws DriveException {
        DriveClient.DriveTokens tokens = drive.exchangeCode(code, redirectUri);
        String folderId = drive.createFolder(tokens.accessToken(), FOLDER_NAME);
        DriveConnection connection = connection().orElseGet(DriveConnection::new);
        connection.setRefreshToken(tokens.refreshToken());
        connection.setAccountEmail(tokens.accountEmail());
        connection.setFolderId(folderId);
        connection.setFolderName(FOLDER_NAME);
        connection.setConnectedBy(currentUser.email().orElse(null));
        connection.setConnectedAt(Instant.now());
        connection.setLastCheckedAt(Instant.now());
        connection.setLastError(null);
        accessToken = tokens.accessToken();
        accessTokenUntil = Instant.now().plusSeconds(50 * 60);
        log.info("Google Drive connected by {} ({}), folder {}", connection.getConnectedBy(), tokens.accountEmail(), folderId);
        return connections.save(connection);
    }

    /** Forgets the connection. Pictures already in Drive stay there; the app keeps serving its cached copies. */
    public void disconnect() {
        connections.deleteById(DriveConnection.SINGLETON_ID);
        accessToken = null;
        accessTokenUntil = Instant.EPOCH;
        log.info("Google Drive disconnected by {}", currentUser.email().orElse("?"));
    }

    /** Verifies the folder is still there and reachable, recording the outcome. */
    public Status checkHealth() {
        Optional<DriveConnection> maybe = connection();
        if (maybe.isEmpty()) {
            return status();
        }
        DriveConnection connection = maybe.get();
        try {
            String token = token(connection);
            Optional<String> name = drive.folderName(token, connection.getFolderId());
            if (name.isEmpty()) {
                markFailed(connection, "The folder is gone from Google Drive (deleted or binned); connect again");
            } else {
                connection.setFolderName(name.get());
                connection.setLastError(null);
            }
        } catch (DriveException e) {
            markFailed(connection, e.getMessage());
        }
        connection.setLastCheckedAt(Instant.now());
        connections.save(connection);
        return status();
    }

    /** Puts a picture in the folder and returns its Drive file id. */
    public String upload(String name, String contentType, byte[] bytes) throws DriveException {
        DriveConnection connection = requireHealthy();
        try {
            String id = drive.upload(token(connection), connection.getFolderId(), name, contentType, bytes);
            connection.setLastCheckedAt(Instant.now());
            connections.save(connection);
            return id;
        } catch (DriveException e) {
            markFailed(connection, e.getMessage());
            connections.save(connection);
            throw e;
        }
    }

    /** The picture's bytes from Drive, empty when it is gone or Drive is unreachable. */
    public Optional<byte[]> download(String driveFileId) {
        Optional<DriveConnection> maybe = connection();
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        try {
            return drive.download(token(maybe.get()), driveFileId);
        } catch (DriveException e) {
            log.warn("Could not fetch {} from Google Drive: {}", driveFileId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Best effort: a picture that cannot be deleted from Drive is logged, not fatal. */
    public void delete(String driveFileId) {
        Optional<DriveConnection> maybe = connection();
        if (maybe.isEmpty()) {
            return;
        }
        try {
            drive.delete(token(maybe.get()), driveFileId);
        } catch (DriveException e) {
            log.warn("Could not delete {} from Google Drive: {}", driveFileId, e.getMessage());
        }
    }

    private DriveConnection requireHealthy() throws DriveException {
        Status status = status();
        return switch (status.state()) {
            case CONNECTED -> status.connection();
            case UNHEALTHY -> throw new DriveException("Google Drive is not working: " + status.connection().getLastError());
            case NOT_CONNECTED -> throw new DriveException("Google Drive is not connected");
            case NOT_CONFIGURED -> throw new DriveException("Google Drive is not set up on this server");
        };
    }

    private String token(DriveConnection connection) throws DriveException {
        String current = accessToken;
        if (current != null && Instant.now().isBefore(accessTokenUntil)) {
            return current;
        }
        String fresh = drive.accessToken(connection.getRefreshToken());
        accessToken = fresh;
        accessTokenUntil = Instant.now().plusSeconds(50 * 60);
        return fresh;
    }

    private static void markFailed(DriveConnection connection, String error) {
        connection.setLastError(error == null ? "Google Drive failed" : error.length() > 480 ? error.substring(0, 480) : error);
    }
}
