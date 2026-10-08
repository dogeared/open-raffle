package org.openraffle.drive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A Google Drive that lives in memory: folders and files are maps, and every failure is a switch. */
public class FakeDriveClient implements DriveClient {

    public boolean configured = true;
    public boolean failUploads;
    public boolean failTokens;
    public boolean folderGone;
    public final Map<String, String> folders = new LinkedHashMap<>();
    /** folder id -> parent folder id (null for a root folder). */
    public final Map<String, String> folderParents = new LinkedHashMap<>();
    /** file id -> folder id it was uploaded into. */
    public final Map<String, String> fileFolders = new LinkedHashMap<>();
    public final Map<String, byte[]> files = new LinkedHashMap<>();
    public final Map<String, String> fileTypes = new LinkedHashMap<>();
    public int tokenRequests;
    private int seq;

    public void reset() {
        configured = true;
        failUploads = false;
        failTokens = false;
        folderGone = false;
        folders.clear();
        folderParents.clear();
        fileFolders.clear();
        files.clear();
        fileTypes.clear();
        tokenRequests = 0;
        seq = 0;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String authorizationUrl(String redirectUri, String state) {
        return "https://accounts.google.test/auth?redirect_uri=" + redirectUri + "&state=" + state;
    }

    @Override
    public DriveTokens exchangeCode(String code, String redirectUri) throws DriveException {
        if (!"good-code".equals(code)) {
            throw new DriveException("Google did not accept the sign-in (400 Bad Request: invalid_grant)");
        }
        return new DriveTokens("access-1", "refresh-" + code, "owner@gmail.test");
    }

    @Override
    public String accessToken(String refreshToken) throws DriveException {
        tokenRequests++;
        if (failTokens) {
            throw new DriveException("Google no longer accepts the connection; connect again");
        }
        return "access-from-" + refreshToken;
    }

    @Override
    public String createFolder(String accessToken, String name, String parentId) {
        String id = "folder-" + (++seq);
        folders.put(id, name);
        folderParents.put(id, parentId);
        return id;
    }

    @Override
    public Optional<String> folderName(String accessToken, String folderId) {
        return folderGone ? Optional.empty() : Optional.ofNullable(folders.get(folderId));
    }

    @Override
    public String upload(String accessToken, String folderId, String name, String contentType, byte[] bytes) throws DriveException {
        if (failUploads) {
            throw new DriveException("Google Drive did not accept the picture (507 Insufficient Storage: quota exceeded)");
        }
        if (!folders.containsKey(folderId)) {
            throw new DriveException("Google Drive did not accept the picture (404 Not Found: File not found: " + folderId + ")");
        }
        String id = "file-" + (++seq);
        files.put(id, bytes);
        fileTypes.put(id, contentType);
        fileFolders.put(id, folderId);
        return id;
    }

    @Override
    public Optional<byte[]> download(String accessToken, String fileId) {
        return Optional.ofNullable(files.get(fileId));
    }

    @Override
    public void delete(String accessToken, String fileId) {
        files.remove(fileId);
        fileTypes.remove(fileId);
    }
}
