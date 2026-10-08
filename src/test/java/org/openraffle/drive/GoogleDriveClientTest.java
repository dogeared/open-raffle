package org.openraffle.drive;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleDriveClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleDriveClient client = new GoogleDriveClient(builder, "client-id", "client-secret");

    @Test
    void isOffUntilBothCredentialsAreSet() {
        assertThat(client.isConfigured()).isTrue();
        assertThat(new GoogleDriveClient(builder, "", "x").isConfigured()).isFalse();
        assertThat(new GoogleDriveClient(builder, "x", " ").isConfigured()).isFalse();
        assertThat(new GoogleDriveClient(builder, null, null).isConfigured()).isFalse();
    }

    @Test
    void theConsentUrlAsksForOfflineAccessToAppFilesOnly() {
        String url = client.authorizationUrl("https://raffle.example.com/drive/callback", "abc123");

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=client-id")
                .contains("redirect_uri=https://raffle.example.com/drive/callback")
                .contains("scope=https://www.googleapis.com/auth/drive.file%20openid%20email")
                .contains("access_type=offline").contains("prompt=consent").contains("state=abc123")
                .doesNotContain("client-secret");
    }

    @Test
    void exchangingTheCodeYieldsTokensAndTheAccountEmail() throws Exception {
        server.expect(requestTo(GoogleDriveClient.TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("code=the-code")))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GoogleDriveClient.USERINFO_URL)).andExpect(header("Authorization", "Bearer at"))
                .andRespond(withSuccess("{\"sub\":\"1\",\"email\":\"owner@gmail.com\"}", MediaType.APPLICATION_JSON));

        DriveClient.DriveTokens tokens = client.exchangeCode("the-code", "https://raffle.example.com/drive/callback");

        assertThat(tokens).isEqualTo(new DriveClient.DriveTokens("at", "rt", "owner@gmail.com"));
        server.verify();
    }

    @Test
    void aGrantWithoutARefreshTokenOrARefusedRefreshIsExplained() {
        server.expect(requestTo(GoogleDriveClient.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GoogleDriveClient.TOKEN_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"));

        assertThatThrownBy(() -> client.exchangeCode("c", "https://x/drive/callback"))
                .isInstanceOf(DriveException.class).hasMessageContaining("did not return a refresh token");
        assertThatThrownBy(() -> client.accessToken("old"))
                .isInstanceOf(DriveException.class).hasMessageContaining("connect again").hasMessageContaining("expired or revoked");
    }

    @Test
    void foldersAreCreatedCheckedAndNoticedWhenBinned() throws Exception {
        server.expect(requestTo(startsWith(GoogleDriveClient.FILES_URL))).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer at"))
                .andExpect(content().string(containsString("application/vnd.google-apps.folder")))
                .andExpect(content().string(containsString("Open Raffle")))
                .andRespond(withSuccess("{\"id\":\"f1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(GoogleDriveClient.FILES_URL + "/f1")))
                .andRespond(withSuccess("{\"id\":\"f1\",\"name\":\"Open Raffle prize pictures\",\"trashed\":false,\"mimeType\":\"application/vnd.google-apps.folder\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(GoogleDriveClient.FILES_URL + "/f1")))
                .andRespond(withSuccess("{\"id\":\"f1\",\"name\":\"Open Raffle prize pictures\",\"trashed\":true,\"mimeType\":\"application/vnd.google-apps.folder\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(GoogleDriveClient.FILES_URL + "/f1"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.createFolder("at", "Open Raffle prize pictures", null)).isEqualTo("f1");
        assertThat(client.folderName("at", "f1")).contains("Open Raffle prize pictures");
        assertThat(client.folderName("at", "f1")).isEmpty(); // binned
        assertThat(client.folderName("at", "f1")).isEmpty(); // gone
    }

    @Test
    void aSubfolderIsCreatedInsideItsParent() throws Exception {
        server.expect(requestTo(startsWith(GoogleDriveClient.FILES_URL))).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"parents\":[\"f1\"]")))
                .andExpect(content().string(containsString("Spring fair (#7)")))
                .andRespond(withSuccess("{\"id\":\"f2\"}", MediaType.APPLICATION_JSON));

        assertThat(client.createFolder("at", "Spring fair (#7)", "f1")).isEqualTo("f2");
    }

    @Test
    void filesAreUploadedAsMultipartRelatedIntoTheFolderThenDownloadedAndDeleted() throws Exception {
        byte[] bytes = "PNGBYTES".getBytes(StandardCharsets.US_ASCII);
        server.expect(requestTo(startsWith(GoogleDriveClient.UPLOAD_URL + "?uploadType=multipart"))).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer at"))
                .andExpect(header("Content-Type", startsWith("multipart/related; boundary=")))
                .andExpect(content().string(containsString("\"parents\":[\"f1\"]")))
                .andExpect(content().string(containsString("\"name\":\"prize-1-0123456789abcdef.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andExpect(content().string(containsString("PNGBYTES")))
                .andRespond(withSuccess("{\"id\":\"file-9\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GoogleDriveClient.FILES_URL + "/file-9?alt=media")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(bytes, MediaType.IMAGE_PNG));
        server.expect(requestTo(GoogleDriveClient.FILES_URL + "/gone?alt=media")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(GoogleDriveClient.FILES_URL + "/file-9")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(GoogleDriveClient.FILES_URL + "/file-9")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)); // already gone is fine
        server.expect(requestTo(startsWith(GoogleDriveClient.UPLOAD_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":403,\"message\":\"The user's Drive storage quota has been exceeded.\"}}"));

        assertThat(client.upload("at", "f1", "prize-1-0123456789abcdef.png", "image/png", bytes)).isEqualTo("file-9");
        assertThat(client.download("at", "file-9")).contains(bytes);
        assertThat(client.download("at", "gone")).isEmpty();
        client.delete("at", "file-9");
        client.delete("at", "file-9");
        assertThatThrownBy(() -> client.upload("at", "f1", "x.png", "image/png", bytes))
                .isInstanceOf(DriveException.class).hasMessageContaining("quota has been exceeded");
        server.verify();
    }
}
