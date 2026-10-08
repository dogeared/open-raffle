package org.openraffle.drive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Google Drive through its REST API v3 and Google's OAuth endpoints, with no SDK. The app
 * asks only for the {@code drive.file} scope (files it created) plus the account email.
 */
@Service
public class GoogleDriveClient implements DriveClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveClient.class);
    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    static final String FILES_URL = "https://www.googleapis.com/drive/v3/files";
    static final String UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files";
    static final String SCOPE = "https://www.googleapis.com/auth/drive.file openid email";
    static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final JsonMapper JSON = JsonMapper.shared();

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;

    public GoogleDriveClient(RestClient.Builder builder,
                             @Value("${raffle.google.client-id:}") String clientId,
                             @Value("${raffle.google.client-secret:}") String clientSecret) {
        this.http = builder.clone().build();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    @Override
    public String authorizationUrl(String redirectUri, String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    @Override
    public DriveTokens exchangeCode(String code, String redirectUri) throws DriveException {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        JsonNode tokens = postForm(TOKEN_URL, form, "Google did not accept the sign-in");
        String accessToken = text(tokens, "access_token");
        String refreshToken = text(tokens, "refresh_token");
        if (accessToken == null || refreshToken == null) {
            throw new DriveException("Google did not return a refresh token; remove the app's access at myaccount.google.com/permissions and connect again");
        }
        String email = null;
        try {
            JsonNode user = get(USERINFO_URL, accessToken, "Could not read the Google account");
            email = text(user, "email");
        } catch (DriveException e) {
            log.warn("Connected to Drive but could not read the account email: {}", e.getMessage());
        }
        return new DriveTokens(accessToken, refreshToken, email);
    }

    @Override
    public String accessToken(String refreshToken) throws DriveException {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");
        JsonNode tokens = postForm(TOKEN_URL, form, "Google no longer accepts the connection; connect again");
        String accessToken = text(tokens, "access_token");
        if (accessToken == null) {
            throw new DriveException("Google no longer accepts the connection; connect again");
        }
        return accessToken;
    }

    @Override
    public String createFolder(String accessToken, String name, String parentId) throws DriveException {
        try {
            Map<String, Object> metadata = parentId == null
                    ? Map.of("name", name, "mimeType", FOLDER_MIME)
                    : Map.of("name", name, "mimeType", FOLDER_MIME, "parents", new String[]{parentId});
            ResponseEntity<byte[]> response = http.post().uri(FILES_URL + "?fields=id")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(metadata))
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            JsonNode node = parse(response, "Could not create the folder in Google Drive");
            String id = text(node, "id");
            if (id == null) {
                throw new DriveException("Google Drive did not return the new folder's id");
            }
            return id;
        } catch (RestClientException e) {
            throw new DriveException("Could not reach Google Drive: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> folderName(String accessToken, String folderId) throws DriveException {
        try {
            ResponseEntity<byte[]> response = http.get().uri(FILES_URL + "/{id}?fields=id,name,trashed,mimeType", folderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            if (response.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            JsonNode node = parse(response, "Could not read the folder in Google Drive");
            if (node.path("trashed").asBoolean(false) || !FOLDER_MIME.equals(text(node, "mimeType"))) {
                return Optional.empty();
            }
            return Optional.ofNullable(text(node, "name"));
        } catch (RestClientException e) {
            throw new DriveException("Could not reach Google Drive: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(String accessToken, String folderId, String name, String contentType, byte[] bytes) throws DriveException {
        String boundary = "open-raffle-" + Long.toHexString(System.nanoTime());
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream(bytes.length + 512);
            String metadata = JSON.writeValueAsString(Map.of("name", name, "parents", new String[]{folderId}));
            body.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("--" + boundary + "\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            ResponseEntity<byte[]> response = http.post().uri(UPLOAD_URL + "?uploadType=multipart&fields=id")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, "multipart/related; boundary=" + boundary)
                    .body(body.toByteArray())
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            JsonNode node = parse(response, "Google Drive did not accept the picture");
            String id = text(node, "id");
            if (id == null) {
                throw new DriveException("Google Drive did not return the picture's id");
            }
            return id;
        } catch (IOException | RestClientException e) {
            throw new DriveException("Could not reach Google Drive: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<byte[]> download(String accessToken, String fileId) throws DriveException {
        try {
            ResponseEntity<byte[]> response = http.get().uri(FILES_URL + "/{id}?alt=media", fileId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            if (response.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DriveException("Google Drive answered " + response.getStatusCode().value() + " for the picture");
            }
            byte[] body = response.getBody();
            return body == null || body.length == 0 ? Optional.empty() : Optional.of(body);
        } catch (RestClientException e) {
            throw new DriveException("Could not reach Google Drive: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String accessToken, String fileId) throws DriveException {
        try {
            ResponseEntity<byte[]> response = http.delete().uri(FILES_URL + "/{id}", fileId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            int status = response.getStatusCode().value();
            if (status != 404 && !response.getStatusCode().is2xxSuccessful()) {
                throw new DriveException("Google Drive answered " + status + " when deleting the picture");
            }
        } catch (RestClientException e) {
            throw new DriveException("Could not reach Google Drive: " + e.getMessage(), e);
        }
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form, String failure) throws DriveException {
        try {
            ResponseEntity<byte[]> response = http.post().uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            return parse(response, failure);
        } catch (RestClientException e) {
            throw new DriveException("Could not reach Google: " + e.getMessage(), e);
        }
    }

    private JsonNode get(String url, String accessToken, String failure) throws DriveException {
        try {
            ResponseEntity<byte[]> response = http.get().uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);
            return parse(response, failure);
        } catch (RestClientException e) {
            throw new DriveException("Could not reach Google: " + e.getMessage(), e);
        }
    }

    private static JsonNode parse(ResponseEntity<byte[]> response, String failure) throws DriveException {
        byte[] body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()) {
            String detail = "";
            if (body != null) {
                try {
                    JsonNode error = JSON.readTree(body);
                    String message = text(error.path("error"), "message");
                    detail = message != null ? ": " + message : text(error, "error_description") != null ? ": " + text(error, "error_description") : "";
                } catch (Exception ignored) {
                    // not JSON; the status is all we know
                }
            }
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            throw new DriveException(failure + " (" + (status == null ? response.getStatusCode().value() : status.value() + " " + status.getReasonPhrase()) + detail + ")");
        }
        if (body == null || body.length == 0) {
            throw new DriveException(failure + " (empty answer)");
        }
        try {
            return JSON.readTree(body);
        } catch (Exception e) {
            throw new DriveException(failure + " (unreadable answer)", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
