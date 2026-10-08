package org.openraffle.drive;

import java.util.Optional;

/**
 * What the app needs from Google Drive, behind an interface so tests can stand in a fake.
 * Access tokens are short-lived; the service obtains one from the stored refresh token.
 */
public interface DriveClient {

    /** False until GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set; the UI then explains. */
    boolean isConfigured();

    /** Where to send the browser so the user can grant the app a folder in their Drive. */
    String authorizationUrl(String redirectUri, String state);

    /** Turns the code Google sends back into tokens and the granting account's email. */
    DriveTokens exchangeCode(String code, String redirectUri) throws DriveException;

    String accessToken(String refreshToken) throws DriveException;

    /** Creates a folder (in My Drive, or inside {@code parentId} when given) and returns its id. */
    String createFolder(String accessToken, String name, String parentId) throws DriveException;

    /** The folder's name if it exists and is not in the bin; empty otherwise. */
    Optional<String> folderName(String accessToken, String folderId) throws DriveException;

    /** Uploads a file into the folder and returns its id. */
    String upload(String accessToken, String folderId, String name, String contentType, byte[] bytes) throws DriveException;

    Optional<byte[]> download(String accessToken, String fileId) throws DriveException;

    void delete(String accessToken, String fileId) throws DriveException;

    /** Tokens from the consent flow; the refresh token is what the app keeps. */
    record DriveTokens(String accessToken, String refreshToken, String accountEmail) {
    }
}
