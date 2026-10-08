package org.openraffle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openraffle.bgg.FakeBgg;
import org.openraffle.domain.PrizePicture;
import org.openraffle.drive.DriveException;
import org.openraffle.drive.FakeDriveClient;
import org.openraffle.image.InvalidImageException;
import org.openraffle.image.TestImages;
import org.openraffle.drive.DriveService;
import org.openraffle.drive.FakeDrive;
import org.openraffle.image.UploadGate;
import org.openraffle.image.UploadRateLimiter;
import org.openraffle.bgg.FakeBggClient;
import org.openraffle.domain.Event;
import org.openraffle.image.PrizeImageStore;
import org.springframework.test.context.TestPropertySource;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({PrizeService.class, EventService.class, DriveService.class, UploadRateLimiter.class, UploadGate.class, FakeBgg.class, FakeDrive.class, PrizeImageStore.class, EventServiceTest.Users.class})
@TestPropertySource(properties = "raffle.images-dir=target/test-images")
class PrizeServiceTest {

    @Autowired
    PrizeService prizeService;

    @Autowired
    TestEntityManager em;

    @Autowired
    org.openraffle.repository.PrizeRepository prizes;
    @Autowired
    FakeDriveClient drive;
    @Autowired
    DriveService driveService;
    @Autowired
    StubCurrentUser user;

    private Event event;

    @BeforeEach
    void event() {
        bgg.downloads.clear();
        bgg.imagesAvailable = true;
        bgg.fullImagesAvailable = true;
        drive.reset();
        user.admin();
        event = event("Fair");
    }

    // --- organizers' own pictures ---------------------------------------------------------

    private Prize linkedToDrive(String name) throws Exception {
        driveService.complete("good-code", "https://x/drive/callback");
        return prizeService.save(prize(name));
    }

    @Test
    void uploadedPicturesAreCheckedReEncodedStoredLocallyAndInDriveAndOrdered() throws Exception {
        Prize bike = linkedToDrive("Bike");

        PrizePicture first = prizeService.addPicture(bike, TestImages.jpeg(800, 600), "image/jpeg", "IMG_1.JPG");
        PrizePicture second = prizeService.addPicture(bike, TestImages.png(300, 300), "image/png", "box.png");

        assertThat(first.getFileName()).matches("prize-" + bike.getId() + "-[a-f0-9]{16}\\.jpg");
        assertThat(second.getFileName()).endsWith(".png");
        assertThat(first.getPosition()).isZero();
        assertThat(second.getPosition()).isEqualTo(1);
        assertThat(first.getUploadedBy()).isEqualTo("admin@example.com");
        assertThat(images.resolve(first.getFileName())).isPresent();
        assertThat(drive.files).containsKey(first.getDriveFileId()).containsKey(second.getDriveFileId());
        // Both went into the event's own subfolder of the app's folder.
        assertThat(drive.fileFolders.get(first.getDriveFileId())).isEqualTo(drive.fileFolders.get(second.getDriveFileId()));
        assertThat(drive.folders.get(drive.fileFolders.get(first.getDriveFileId()))).isEqualTo(event.getDriveFolderName());
        assertThat(drive.folderParents.get(drive.fileFolders.get(first.getDriveFileId()))).isEqualTo("folder-1");
        assertThat(drive.fileTypes.get(first.getDriveFileId())).isEqualTo("image/jpeg");
        // What went to Drive is the re-encoded picture, not the upload.
        assertThat(drive.files.get(first.getDriveFileId())).isEqualTo(java.nio.file.Files.readAllBytes(images.resolve(first.getFileName()).orElseThrow()));

        Prize stored = prizes.findById(bike.getId()).orElseThrow();
        assertThat(stored.getPictures()).extracting(PrizePicture::getFileName).containsExactly(first.getFileName(), second.getFileName());
        assertThat(stored.getImageUrl()).isEqualTo("images/" + first.getFileName());
        assertThat(stored.getGalleryUrls()).containsExactly("images/" + first.getFileName(), "images/" + second.getFileName());

        // Moving the second to the front makes it the primary; the BGG image joins the gallery last.
        prizeService.movePicture(stored, second, -1);
        stored = prizes.findById(bike.getId()).orElseThrow();
        assertThat(stored.getPictures()).extracting(PrizePicture::getFileName).containsExactly(second.getFileName(), first.getFileName());
        assertThat(stored.getPictures()).extracting(PrizePicture::getPosition).containsExactly(0, 1);
        stored.setBggId(13L);
        stored = prizeService.save(stored);
        assertThat(stored.getGalleryUrls()).hasSize(3).last().isEqualTo("images/" + stored.getImageFile());
        assertThat(stored.getImageUrl()).isEqualTo("images/" + second.getFileName());

        // Removing cleans the cache and Drive and renumbers.
        prizeService.removePicture(stored, second);
        stored = prizes.findById(bike.getId()).orElseThrow();
        assertThat(stored.getPictures()).extracting(PrizePicture::getFileName).containsExactly(first.getFileName());
        assertThat(stored.getPictures().get(0).getPosition()).isZero();
        assertThat(images.resolve(second.getFileName())).isEmpty();
        assertThat(drive.files).doesNotContainKey(second.getDriveFileId());
    }

    @Test
    void uploadsAreRefusedWhenTheyShouldBeAndNothingIsKept() throws Exception {
        Prize bike = linkedToDrive("Bike");

        assertThatThrownBy(() -> prizeService.addPicture(bike, "<svg/>".getBytes(), "image/svg+xml", "x.svg"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("Only JPEG");
        assertThat(prizes.findById(bike.getId()).orElseThrow().getPictures()).isEmpty();
        assertThat(drive.files).isEmpty();

        // Drive refusing: the local copy is not kept either.
        long filesBefore = cachedFiles();
        drive.failUploads = true;
        assertThatThrownBy(() -> prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "a.png"))
                .isInstanceOf(DriveException.class).hasMessageContaining("quota");
        assertThat(cachedFiles()).isEqualTo(filesBefore);
        drive.failUploads = false;

        // Now unhealthy: refused before any work.
        assertThatThrownBy(() -> prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "a.png"))
                .isInstanceOf(DriveException.class).hasMessageContaining("Google Drive");
        driveService.checkHealth();

        // At most ten.
        for (int i = 0; i < Prize.MAX_PICTURES; i++) {
            prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "p" + i + ".png");
        }
        assertThatThrownBy(() -> prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "eleven.png"))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("at most 10");

        // Only someone who runs the event.
        user.organizer("stranger@example.com");
        assertThatThrownBy(() -> prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "a.png"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> prizeService.removePicture(bike, prizes.findById(bike.getId()).orElseThrow().getPictures().get(0)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void withoutADriveConnectionUploadsAreOffAndTheBggPictureStillWorks() {
        Prize bike = prizeService.save(prize("Bike"));

        assertThatThrownBy(() -> prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "a.png"))
                .isInstanceOf(DriveException.class).hasMessageContaining("until Google Drive is connected");
        bike.setBggId(13L);
        assertThat(prizeService.save(bike).getImageUrl()).startsWith("images/prize-");
    }

    @Test
    void aLostUploadComesBackFromDriveAndFailingThatTheBggPictureStandsIn() throws Exception {
        Prize bike = linkedToDrive("Bike");
        bike.setBggId(13L);
        bike = prizeService.save(bike);
        PrizePicture picture = prizeService.addPicture(bike, TestImages.png(20, 20), "image/png", "a.png");
        java.nio.file.Files.delete(images.resolve(picture.getFileName()).orElseThrow());

        // From Drive, under the same name.
        assertThat(prizeService.restoreImage(picture.getFileName())).isPresent();
        assertThat(images.resolve(picture.getFileName())).isPresent();

        // Gone from Drive too: the BGG box image is served in its place.
        java.nio.file.Files.delete(images.resolve(picture.getFileName()).orElseThrow());
        drive.files.remove(picture.getDriveFileId());
        java.nio.file.Path served = prizeService.restoreImage(picture.getFileName()).orElseThrow();
        assertThat(served.getFileName().toString()).isEqualTo(prizes.findById(bike.getId()).orElseThrow().getImageFile());
        assertThat(images.resolve(picture.getFileName())).isEmpty();
    }

    @Test
    void anUploadStreamedToAFileIsProcessedFromThereAndTheGateIsReleasedEvenWhenItIsRefused() throws Exception {
        Prize bike = linkedToDrive("Bike");
        java.nio.file.Path photo = java.nio.file.Files.createTempFile("upload", ".jpg");
        java.nio.file.Files.write(photo, TestImages.jpeg(3500, 2000));

        PrizePicture picture = prizeService.addPicture(bike, photo, "image/jpeg", "photo.jpg");
        assertThat(picture.getFileName()).endsWith(".jpg");
        assertThat(photo).exists(); // the caller owns the temp file

        java.nio.file.Files.write(photo, "<html>".getBytes());
        assertThatThrownBy(() -> prizeService.addPicture(bike, photo, "image/jpeg", "photo.jpg"))
                .isInstanceOf(InvalidImageException.class);
        java.nio.file.Files.delete(photo);
        // The gate let the next one in: a refused picture does not hold a permit.
        assertThat(prizeService.addPicture(bike, TestImages.png(10, 10), "image/png", "ok.png")).isNotNull();
    }

    @Test
    void deletingAPrizeRemovesItsUploadsEverywhere() throws Exception {
        Prize bike = linkedToDrive("Bike");
        PrizePicture picture = prizeService.addPicture(bike, TestImages.png(20, 20), "image/png", "a.png");

        prizeService.delete(prizes.findById(bike.getId()).orElseThrow());

        assertThat(images.resolve(picture.getFileName())).isEmpty();
        assertThat(drive.files).isEmpty();
        assertThat(prizes.findById(bike.getId())).isEmpty();
    }

    @Autowired
    FakeBggClient bgg;
    @Autowired
    PrizeImageStore images;

    @Test
    void linkingAGameDownloadsItsBoxImageOnceAndUnlinkingRemovesIt() {
        Prize prize = prize("Bike");
        prize.setBggId(13L);

        Prize saved = prizeService.save(prize);

        assertThat(saved.getBggName()).isEqualTo("CATAN");
        assertThat(saved.getBggImageId()).isEqualTo(13L);
        assertThat(saved.getImageFile()).matches("prize-" + saved.getId() + "-[a-f0-9]{16}\\.png");
        assertThat(saved.getImageUrl()).isEqualTo("images/" + saved.getImageFile());
        assertThat(saved.getBggUrl()).isEqualTo("https://boardgamegeek.com/boardgame/13");
        assertThat(images.resolve(saved.getImageFile())).isPresent();
        assertThat(bgg.downloads).containsExactly(FakeBggClient.imageUrl(13));

        // Saving again without changing the link does not call BGG again.
        saved.setDescription("Red, 21 gears");
        saved = prizeService.save(saved);
        assertThat(bgg.downloads).hasSize(1);

        // Re-linking to another game swaps the picture.
        String first = saved.getImageFile();
        saved.setBggId(822L);
        saved = prizeService.save(saved);
        assertThat(saved.getImageFile()).isNotEqualTo(first);
        assertThat(saved.getBggName()).isEqualTo("CATAN"); // the organizer's label is kept
        assertThat(images.resolve(first)).isEmpty();
        assertThat(bgg.downloads).hasSize(2);

        // Unlinking drops the picture and the BGG details.
        String second = saved.getImageFile();
        saved.setBggId(null);
        saved = prizeService.save(saved);
        assertThat(saved.hasImage()).isFalse();
        assertThat(saved.getBggImageId()).isNull();
        assertThat(saved.getBggName()).isNull();
        assertThat(images.resolve(second)).isEmpty();
    }

    @Test
    void aGameWithoutArtOrAnUnreachableBggStillSaves() {
        bgg.imagesAvailable = false;
        Prize noArt = prize("Bike");
        noArt.setBggId(13L);
        Prize saved = prizeService.save(noArt);
        assertThat(saved.hasImage()).isFalse();
        assertThat(saved.getBggImageId()).isEqualTo(13L); // linked, nothing to fetch later

        Prize unknown = prize("Mug");
        unknown.setBggId(123456L); // not a thing the fake knows
        saved = prizeService.save(unknown);
        assertThat(saved.getBggId()).isEqualTo(123456L);
        assertThat(saved.getBggImageId()).isNull(); // so the next save retries
        assertThat(saved.hasImage()).isFalse();
    }

    @Test
    void aLostPictureIsFetchedAgainUnderTheSameName() throws java.io.IOException {
        Prize prize = prize("Bike");
        prize.setBggId(13L);
        Prize saved = prizeService.save(prize);
        String file = saved.getImageFile();
        java.nio.file.Files.delete(images.resolve(file).orElseThrow());
        assertThat(images.resolve(file)).isEmpty();

        assertThat(prizeService.restoreImage(file)).isPresent();
        assertThat(images.resolve(file)).isPresent();
        assertThat(bgg.downloads).hasSize(2);

        // Names nobody uses, prizes without a BGG link, and junk are simply not restored.
        assertThat(prizeService.restoreImage("prize-999-0123456789abcdef.png")).isEmpty();
        assertThat(prizeService.restoreImage("../etc/passwd")).isEmpty();
        assertThat(prizeService.restoreImage(null)).isEmpty();
    }

    @Test
    void theThumbnailStandsInWhenTheFullImageIsUnusable() {
        bgg.fullImagesAvailable = false;
        Prize prize = prize("Bike");
        prize.setBggId(13L);

        Prize saved = prizeService.save(prize);

        assertThat(saved.hasImage()).isTrue();
        assertThat(bgg.downloads).containsExactly(FakeBggClient.imageUrl(13), FakeBggClient.imageUrl(13) + "?thumb");
    }

    @Test
    void theBggRatingIsCachedOnLinkAndRefreshedOnlyOnceItIsAMonthOld() {
        Prize prize = prize("Bike");
        prize.setBggId(13L);
        Prize saved = prizeService.save(prize);
        assertThat(saved.getBggRating()).isEqualTo(7.09005);
        assertThat(saved.getBggRatingCount()).isEqualTo(1000);
        assertThat(saved.getBggRatingAt()).isNotNull();

        // Fresh enough: a plain save leaves it alone even though BGG now says otherwise.
        bgg.ratings.put(13L, 8.5);
        saved.setDescription("Red");
        saved = prizeService.save(saved);
        assertThat(saved.getBggRating()).isEqualTo(7.09005);

        // A month on, the next save refreshes it.
        saved.setBggRatingAt(java.time.Instant.now().minus(PrizeService.RATING_MAX_AGE).minusSeconds(60));
        saved = prizeService.save(saved);
        assertThat(saved.getBggRating()).isEqualTo(8.5);
        assertThat(bgg.downloads).hasSize(1); // the picture was not fetched again
    }

    @Test
    void theBackgroundRefresherFillsInRatingsThatWereNeverFetchedAndOldOnes() {
        Prize neverFetched = prize("Dead Cells");
        neverFetched.setBggId(13L);
        neverFetched.setBggImageId(13L); // linked before ratings existed: no rating, no fetch date
        neverFetched = prizes.save(neverFetched);
        Prize old = prize("Carcassonne");
        old.setBggId(822L);
        old.setBggImageId(822L);
        old.setBggRating(6.0);
        old.setBggRatingCount(1000);
        old.setBggRatingAt(java.time.Instant.now().minus(PrizeService.RATING_MAX_AGE).minusSeconds(60));
        old = prizes.save(old);
        Prize fresh = prize("Seafarers");
        fresh.setBggId(2655L);
        fresh.setBggImageId(2655L);
        fresh.setBggRating(5.0);
        fresh.setBggRatingCount(1000);
        fresh.setBggRatingAt(java.time.Instant.now());
        fresh = prizes.save(fresh);
        prize("Mug"); // not linked at all

        Prize noCount = prize("Dominion");
        noCount.setBggId(2655L);
        noCount.setBggImageId(2655L);
        noCount.setBggRating(7.0);
        noCount.setBggRatingAt(java.time.Instant.now());
        noCount = prizes.save(noCount);

        assertThat(prizeService.findWithStaleRating()).containsExactlyInAnyOrder(neverFetched, old, noCount);

        new PrizeRatingRefresher(prizeService, bgg, 0).refreshStaleRatings();

        assertThat(prizes.findById(neverFetched.getId()).orElseThrow().getBggRating()).isEqualTo(7.09005);
        assertThat(prizes.findById(old.getId()).orElseThrow().getBggRating()).isEqualTo(7.4);
        assertThat(prizes.findById(fresh.getId()).orElseThrow().getBggRating()).isEqualTo(5.0);
        assertThat(prizes.findById(noCount.getId()).orElseThrow().getBggRatingCount()).isEqualTo(1000);
        assertThat(prizeService.findWithStaleRating()).isEmpty();
        assertThat(bgg.downloads).isEmpty(); // ratings only; no pictures fetched
    }

    @Test
    void deletingAPrizeDeletesItsPicture() {
        Prize prize = prize("Bike");
        prize.setBggId(13L);
        Prize saved = prizeService.save(prize);
        String file = saved.getImageFile();

        prizeService.delete(saved);

        assertThat(images.resolve(file)).isEmpty();
    }

    @Test
    void prizesAreListedAlphabeticallyPerEvent() {
        Event other = event("Other fair");
        prizeService.save(prize("mug"));
        prizeService.save(prize("Bike"));
        prizeService.save(prize("book"));
        Prize x = new Prize();
        x.setEvent(other);
        x.setName("Apple");
        prizeService.save(x);

        assertThat(prizeService.findAll(event)).extracting(Prize::getName).containsExactly("Bike", "book", "mug");
        assertThat(prizeService.findAll(other)).extracting(Prize::getName).containsExactly("Apple");
    }

    @Test
    void claimRecordsTheWinnerAndUnclaimClearsIt() {
        Participant winner = participant("Ann", 1, 5);
        Prize bike = prizeService.save(prize("Bike"));

        Prize claimed = prizeService.claim(bike, winner);
        assertThat(claimed.isClaimed()).isTrue();
        assertThat(claimed.isClaimedBy(winner)).isTrue();
        assertThat(claimed.getClaimedAt()).isNotNull();

        Prize released = prizeService.unclaim(bike);
        assertThat(released.isClaimed()).isFalse();
        assertThat(released.getClaimedAt()).isNull();
    }

    @Test
    void claimingSomeoneElsesPrizeFails() {
        Participant ann = participant("Ann", 1, 5);
        Participant bob = participant("Bob", 6, 10);
        Prize bike = prizeService.save(prize("Bike"));
        prizeService.claim(bike, ann);

        assertThatThrownBy(() -> prizeService.claim(bike, bob))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ann");
        // Re-claiming by the same winner is harmless.
        assertThat(prizeService.claim(bike, ann).isClaimedBy(ann)).isTrue();
    }

    private long cachedFiles() throws java.io.IOException {
        try (var files = java.nio.file.Files.list(images.directory())) {
            return files.count();
        }
    }

    private Prize prize(String name) {
        Prize prize = new Prize();
        prize.setEvent(event);
        prize.setName(name);
        return prize;
    }

    private Event event(String name) {
        Event e = new Event();
        e.setName(name);
        return em.persistAndFlush(e);
    }

    private Participant participant(String name, long start, long end) {
        Participant p = new Participant();
        p.setEvent(event);
        p.setName(name);
        p.addRange(start, end);
        p.setPhone("555-0100");
        p.setToken("token-" + name);
        return em.persistAndFlush(p);
    }
}
