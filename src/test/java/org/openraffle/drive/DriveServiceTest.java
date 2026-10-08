package org.openraffle.drive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.DriveConnection;
import org.openraffle.domain.Event;
import org.openraffle.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({DriveService.class, FakeDrive.class, DriveServiceTest.Users.class})
class DriveServiceTest {

    @TestConfiguration
    static class Users {
        @Bean
        CurrentUser currentUser() {
            return new CurrentUser() {
                public Optional<String> email() { return Optional.of("pat@example.com"); }
                public String displayName() { return "Pat"; }
                public boolean isAdmin() { return false; }
                public boolean isOrganizer() { return true; }
            };
        }
    }

    @Autowired
    DriveService driveService;
    @Autowired
    FakeDriveClient drive;
    @Autowired
    org.openraffle.repository.EventRepository events;

    private Event event(String name) {
        Event e = new Event();
        e.setName(name);
        return events.save(e);
    }

    @BeforeEach
    void reset() {
        drive.reset();
    }

    @Test
    void connectingCreatesTheAppsFolderAndRemembersWhoAndWhere() throws Exception {
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.NOT_CONNECTED);
        assertThat(driveService.canUpload()).isFalse();

        DriveConnection c = driveService.complete("good-code", "https://raffle.example.com/drive/callback");

        assertThat(c.getFolderId()).isEqualTo("folder-1");
        assertThat(drive.folders).containsEntry("folder-1", DriveService.FOLDER_NAME);
        assertThat(c.getAccountEmail()).isEqualTo("owner@gmail.test");
        assertThat(c.getConnectedBy()).isEqualTo("pat@example.com");
        assertThat(c.getRefreshToken()).isEqualTo("refresh-good-code");
        assertThat(c.isHealthy()).isTrue();
        assertThat(c.getFolderUrl()).isEqualTo("https://drive.google.com/drive/folders/folder-1");
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.CONNECTED);
        assertThat(driveService.canUpload()).isTrue();

        // Reconnecting replaces the connection rather than adding a second one.
        driveService.complete("good-code", "https://raffle.example.com/drive/callback");
        assertThat(driveService.connection().orElseThrow().getFolderId()).isEqualTo("folder-2");
    }

    @Test
    void aBadCodeOrMissingCredentialsAreReported() {
        assertThatThrownBy(() -> driveService.complete("bad-code", "https://x/drive/callback"))
                .isInstanceOf(DriveException.class).hasMessageContaining("did not accept");
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.NOT_CONNECTED);

        drive.configured = false;
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.NOT_CONFIGURED);
        assertThatThrownBy(() -> driveService.upload(event("Fair"), "x.png", "image/png", new byte[]{1}))
                .isInstanceOf(DriveException.class).hasMessageContaining("not set up");
    }

    @Test
    void eachEventGetsItsOwnSubfolderMadeOnFirstUploadAndRemadeWhenGoneOrAfterAReconnect() throws Exception {
        driveService.complete("good-code", "https://x/drive/callback");
        Event fair = event("Spring fair");
        Event gala = event("Winter gala");

        String a = driveService.upload(fair, "a.png", "image/png", new byte[]{1});
        String b = driveService.upload(fair, "b.png", "image/png", new byte[]{2});
        String c = driveService.upload(gala, "c.png", "image/png", new byte[]{3});

        String fairFolder = drive.fileFolders.get(a);
        Event storedFair = events.findById(fair.getId()).orElseThrow();
        assertThat(storedFair.getDriveFolderId()).isEqualTo(fairFolder);
        assertThat(drive.folders.get(fairFolder)).isEqualTo("Spring fair (#" + fair.getId() + ")");
        assertThat(drive.folderParents.get(fairFolder)).isEqualTo("folder-1"); // inside the app's folder
        assertThat(drive.fileFolders.get(b)).isEqualTo(fairFolder); // reused, not re-created
        String galaFolder = drive.fileFolders.get(c);
        assertThat(galaFolder).isNotEqualTo(fairFolder);
        assertThat(drive.folders.get(galaFolder)).startsWith("Winter gala");
        assertThat(drive.folderParents.get(galaFolder)).isEqualTo("folder-1");

        // Someone deleted the subfolder in Drive: the next upload makes a new one and still succeeds.
        drive.folders.remove(fairFolder);
        String d = driveService.upload(fair, "d.png", "image/png", new byte[]{4});
        String newFairFolder = drive.fileFolders.get(d);
        assertThat(newFairFolder).isNotEqualTo(fairFolder);
        assertThat(drive.folderParents.get(newFairFolder)).isEqualTo("folder-1");
        assertThat(events.findById(fair.getId()).orElseThrow().getDriveFolderId()).isEqualTo(newFairFolder);
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.CONNECTED);

        // Reconnecting (new root folder) starts the event's subfolder over in the new account.
        driveService.complete("good-code", "https://x/drive/callback");
        String e = driveService.upload(fair, "e.png", "image/png", new byte[]{5});
        assertThat(drive.folderParents.get(drive.fileFolders.get(e))).isEqualTo(driveService.connection().orElseThrow().getFolderId());
    }

    @Test
    void uploadsDownloadsAndDeletesGoThroughTheFolderWithOneAccessTokenPerHour() throws Exception {
        driveService.complete("good-code", "https://x/drive/callback");
        drive.tokenRequests = 0;

        String id = driveService.upload(event("Fair"), "prize-1-0123456789abcdef.png", "image/png", new byte[]{1, 2});
        assertThat(drive.files).containsKey(id);
        assertThat(driveService.download(id)).contains(new byte[]{1, 2});
        driveService.delete(id);
        assertThat(driveService.download(id)).isEmpty();
        assertThat(drive.tokenRequests).isZero(); // the token from the consent flow is still fresh
    }

    @Test
    void failuresMarkTheConnectionUnhealthyUntilACheckOrReconnectClearsThem() throws Exception {
        driveService.complete("good-code", "https://x/drive/callback");

        Event fair = event("Fair");
        drive.failUploads = true;
        assertThatThrownBy(() -> driveService.upload(fair, "a.png", "image/png", new byte[]{1}))
                .isInstanceOf(DriveException.class).hasMessageContaining("quota");
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.UNHEALTHY);
        assertThat(driveService.connection().orElseThrow().getLastError()).contains("quota");
        assertThatThrownBy(() -> driveService.upload(fair, "b.png", "image/png", new byte[]{1}))
                .isInstanceOf(DriveException.class).hasMessageContaining("not working");

        drive.failUploads = false;
        assertThat(driveService.checkHealth().state()).isEqualTo(DriveService.State.CONNECTED);

        drive.folderGone = true;
        assertThat(driveService.checkHealth().state()).isEqualTo(DriveService.State.UNHEALTHY);
        assertThat(driveService.connection().orElseThrow().getLastError()).contains("folder is gone");

        drive.folderGone = false;
        drive.failTokens = true;
        driveService.disconnect();
        assertThat(driveService.status().state()).isEqualTo(DriveService.State.NOT_CONNECTED);
        assertThat(driveService.download("file-1")).isEmpty();
    }
}
