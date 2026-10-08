package org.openraffle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openraffle.bgg.FakeBgg;
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
@Import({PrizeService.class, FakeBgg.class, PrizeImageStore.class})
@TestPropertySource(properties = "raffle.images-dir=target/test-images")
class PrizeServiceTest {

    @Autowired
    PrizeService prizeService;

    @Autowired
    TestEntityManager em;

    private Event event;

    @BeforeEach
    void event() {
        bgg.downloads.clear();
        bgg.imagesAvailable = true;
        bgg.fullImagesAvailable = true;
        event = event("Fair");
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
