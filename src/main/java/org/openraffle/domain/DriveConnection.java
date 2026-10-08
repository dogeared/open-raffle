package org.openraffle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * The app's one connection to a Google Drive folder, where organizers' prize pictures are
 * kept. A single row (id 1). The refresh token is for the {@code drive.file} scope only:
 * it can touch files this app created and nothing else in the account.
 */
@Entity
public class DriveConnection {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 2048)
    private String refreshToken;

    /** The Google account that granted access. */
    private String accountEmail;

    @Column(nullable = false, length = 128)
    private String folderId;

    private String folderName;

    /** Email of the organizer or admin who connected it. */
    private String connectedBy;

    private Instant connectedAt;

    private Instant lastCheckedAt;

    /** Null while the connection works; otherwise what went wrong last time. */
    @Column(length = 500)
    private String lastError;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getConnectedBy() {
        return connectedBy;
    }

    public void setConnectedBy(String connectedBy) {
        this.connectedBy = connectedBy;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isHealthy() {
        return lastError == null;
    }

    public String getFolderUrl() {
        return "https://drive.google.com/drive/folders/" + folderId;
    }
}
