package org.openraffle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openraffle.bgg.FakeBgg;
import org.openraffle.domain.Event;
import org.openraffle.image.PrizeImageStore;
import org.springframework.test.context.TestPropertySource;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.domain.TicketRange;
import org.openraffle.repository.ParticipantRepository;
import org.openraffle.repository.PrizeRepository;
import org.openraffle.service.ParticipantService.TicketRangeConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ParticipantService.class, PrizeService.class, FakeBgg.class, PrizeImageStore.class})
@TestPropertySource(properties = "raffle.images-dir=target/test-images")
class ParticipantServiceTest {

    @Autowired
    ParticipantService participantService;

    @Autowired
    PrizeService prizeService;

    @Autowired
    ParticipantRepository participants;

    @Autowired
    PrizeRepository prizes;

    @Autowired
    TestEntityManager em;

    private Event event;

    @BeforeEach
    void event() {
        event = event("Fair");
    }

    @Test
    void ticketRangesAreScopedToTheEvent() {
        Event other = event("Other fair");
        participantService.save(participant("Ann", 1, 10));
        Participant bob = participant("Bob", 1, 10);
        bob.setEvent(other);

        assertThat(participantService.save(bob).getId()).isNotNull();
        assertThat(participantService.findByTicket(event, 5)).get().extracting(Participant::getName).isEqualTo("Ann");
        assertThat(participantService.findByTicket(other, 5)).get().extracting(Participant::getName).isEqualTo("Bob");
        assertThat(participantService.findAll(event)).extracting(Participant::getName).containsExactly("Ann");
    }

    @Test
    void organizersMustEnterAPhoneNumber() {
        Participant noPhone = participant("Quiet", 1, 1);
        noPhone.setPhone(" ");

        assertThatThrownBy(() -> participantService.save(noPhone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone");
    }

    @Test
    void phoneNumbersMayBeInternational() {
        for (String ok : List.of("555-123-4567", "(555) 123-4567", "+44 20 7946 0958", "+49 30 901820", "+81-3-1234-5678", "5551234567")) {
            assertThat(Participant.isPlausiblePhone(ok)).as(ok).isTrue();
        }
        for (String bad : List.of("abc", "+", "123", "+1 555 123 4567 ext 12", "12345678901234567", "")) {
            assertThat(Participant.isPlausiblePhone(bad)).as(bad).isFalse();
        }

        Participant intl = participant("Nigel", 1, 1);
        intl.setPhone("+44 20 7946 0958");
        assertThat(participantService.save(intl).getPhone()).isEqualTo("+44 20 7946 0958");

        Participant garbage = participant("Garbage", 2, 2);
        garbage.setPhone("call me maybe");
        assertThatThrownBy(() -> participantService.save(garbage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("country code");
    }

    @Test
    void wishlistUpdatesStillWorkForParticipantsWithoutAPhone() {
        // Participants created before phone numbers were required have none.
        Participant legacy = participant("Legacy", 1, 1);
        legacy.setPhone(null);
        legacy.setToken("legacy-token");
        em.persistAndFlush(legacy);
        Prize bike = prizeService.save(prize("Bike"));

        participantService.updateWishlist("legacy-token", List.of(bike));
        em.flush();
        em.clear();

        assertThat(participants.findByToken("legacy-token")).get()
                .extracting(p -> p.getWishlist().size()).isEqualTo(1);
    }

    @Test
    void participantsMustBelongToAnEvent() {
        Participant orphan = participant("Nobody", 1, 1);
        orphan.setEvent(null);

        assertThatThrownBy(() -> participantService.save(orphan)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveIssuesAnUnguessableTokenThatLooksTheParticipantUp() {
        Participant saved = participantService.save(participant("Ann", 1, 10));

        assertThat(saved.getToken()).hasSizeGreaterThanOrEqualTo(32).doesNotContain("=", "+", "/");
        assertThat(participantService.findByToken(saved.getToken())).contains(saved);
        assertThat(participantService.findByToken("nope")).isEmpty();
    }

    @Test
    void overlappingTicketRangesAreRejected() {
        participantService.save(participant("Ann", 1, 10));

        assertThatThrownBy(() -> participantService.save(participant("Bob", 10, 20)))
                .isInstanceOf(TicketRangeConflictException.class)
                .hasMessageContaining("Ann (1 – 10)");
        assertThatThrownBy(() -> participantService.save(participant("Cat", 0, 100)))
                .isInstanceOf(TicketRangeConflictException.class);
        assertThat(participantService.save(participant("Dan", 11, 20)).getId()).isNotNull();
    }

    @Test
    void editingAParticipantDoesNotConflictWithItself() {
        Participant ann = participantService.save(participant("Ann", 1, 10));

        ann.setRanges(new ArrayList<>(List.of(new TicketRange(1, 12))));

        assertThat(participantService.save(ann).getTicketRangeLabel()).isEqualTo("1 – 12");
    }

    @Test
    void returningBuyersGetAnotherRange() {
        Participant ann = participantService.save(participant("Ann", 1, 10));
        participantService.save(participant("Bob", 11, 20));

        ann.addRange(30, 35);
        Participant saved = participantService.save(ann);

        assertThat(saved.getTicketRangeLabel()).isEqualTo("1 – 10, 30 – 35");
        assertThat(saved.getTicketCount()).isEqualTo(16);
        assertThat(participantService.findByTicket(event, 33)).contains(saved);
        assertThat(participantService.findByTicket(event, 25)).isEmpty();
        assertThat(participantService.findAll(event)).extracting(Participant::getName).containsExactly("Ann", "Bob");
    }

    @Test
    void participantsAreListedAlphabeticallyRegardlessOfTickets() {
        participantService.save(participant("zoe", 1, 5));
        participantService.save(participant("Bob", 50, 55));
        participantService.save(participant("ann", 20, 25));
        participantService.save(participant("Ann", 30, 35));

        assertThat(participantService.findAll(event)).extracting(Participant::getName)
                .containsExactly("ann", "Ann", "Bob", "zoe");
    }

    @Test
    void prefixedRollsAreLookedUpAsPrintedAndOnlyClashWithinTheirPrefix() {
        Participant ann = participant("Ann", 1, 10);
        ann.setRanges(new ArrayList<>(List.of(TicketRange.of("987-001", "987-100"))));
        participantService.save(ann);
        Participant bob = participant("Bob", 1, 10);
        bob.setRanges(new ArrayList<>(List.of(TicketRange.of("4563-100-300", "4563-100-1000"))));
        participantService.save(bob);
        Participant cat = participant("Cat", 1, 10);
        cat.setRanges(new ArrayList<>(List.of(TicketRange.of("4564-100-300", "4564-100-1000"), TicketRange.of("1", "50"))));
        participantService.save(cat);

        assertThat(participantService.findByTicket(event, "987-042")).get().extracting(Participant::getName).isEqualTo("Ann");
        assertThat(participantService.findByTicket(event, "987-42")).get().extracting(Participant::getName).isEqualTo("Ann");
        assertThat(participantService.findByTicket(event, "4563-100-500")).get().extracting(Participant::getName).isEqualTo("Bob");
        assertThat(participantService.findByTicket(event, "4564-100-500")).get().extracting(Participant::getName).isEqualTo("Cat");
        assertThat(participantService.findByTicket(event, "42")).get().extracting(Participant::getName).isEqualTo("Cat");
        assertThat(participantService.findByTicket(event, "988-042")).isEmpty();
        assertThat(participantService.findByTicket(event, "not a ticket")).isEmpty();

        Participant dan = participant("Dan", 1, 10);
        dan.setRanges(new ArrayList<>(List.of(TicketRange.of("987-050", "987-060"))));
        assertThatThrownBy(() -> participantService.save(dan))
                .isInstanceOf(TicketRangeConflictException.class)
                .hasMessageContaining("Ann (987-001 – 987-100)");
    }

    @Test
    void aSecondRangeMayNotOverlapAnyoneElse() {
        participantService.save(participant("Ann", 1, 10));
        Participant bob = participantService.save(participant("Bob", 11, 20));

        bob.addRange(8, 9);

        assertThatThrownBy(() -> participantService.save(bob))
                .isInstanceOf(TicketRangeConflictException.class)
                .hasMessageContaining("Ann (1 – 10)");
    }

    @Test
    void aParticipantsOwnRangesMayNotOverlapEachOther() {
        Participant ann = participant("Ann", 1, 10);
        ann.addRange(5, 12);

        assertThatThrownBy(() -> participantService.save(ann))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap each other");
        assertThat(participants.count()).isZero();
    }

    @Test
    void atLeastOneRangeIsRequired() {
        Participant nobody = participant("Nobody", 1, 1);
        nobody.setRanges(new ArrayList<>());

        assertThatThrownBy(() -> participantService.save(nobody))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> participantService.save(participant("Ann", 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByTicketCoversTheWholeRangeInclusive() {
        Participant ann = participantService.save(participant("Ann", 100, 104));

        assertThat(participantService.findByTicket(event, 100)).contains(ann);
        assertThat(participantService.findByTicket(event, 104)).contains(ann);
        assertThat(participantService.findByTicket(event, 99)).isEmpty();
        assertThat(participantService.findByTicket(event, 105)).isEmpty();
    }

    @Test
    void updateWishlistKeepsTheParticipantsOrder() {
        Participant ann = participantService.save(participant("Ann", 1, 10));
        Prize bike = prizeService.save(prize("Bike"));
        Prize book = prizeService.save(prize("Book"));
        Prize mug = prizeService.save(prize("Mug"));

        participantService.updateWishlist(ann.getToken(), List.of(mug, bike, book));
        em.flush();
        em.clear();

        Participant reloaded = participants.findByToken(ann.getToken()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Mug", "Bike", "Book");
        assertThat(reloaded.getWishlistUpdatedAt()).isNotNull();

        participantService.updateWishlist(ann.getToken(), List.of(book));
        em.flush();
        em.clear();
        reloaded = participants.findByToken(ann.getToken()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Book");
    }

    @Test
    void addToWishlistAppendsOnceAtTheBottom() {
        Participant ann = participantService.save(participant("Ann", 1, 10));
        Prize bike = prizeService.save(prize("Bike"));
        Prize mug = prizeService.save(prize("Mug"));
        participantService.updateWishlist(ann.getToken(), List.of(bike));

        participantService.addToWishlist(ann, mug);
        participantService.addToWishlist(ann, mug);
        participantService.addToWishlist(ann, bike);
        em.flush();
        em.clear();

        Participant reloaded = participants.findByToken(ann.getToken()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Bike", "Mug");
    }

    @Test
    void organizersCanTakeAPrizeOffAListAndItReleasesTheirClaim() {
        Participant ann = participantService.save(participant("Ann", 1, 10));
        Prize bike = prizeService.save(prize("Bike"));
        Prize mug = prizeService.save(prize("Mug"));
        participantService.updateWishlist(ann.getToken(), List.of(bike, mug));
        prizeService.claim(bike, ann);

        participantService.removeFromWishlist(ann, bike);
        em.flush();
        em.clear();

        Participant reloaded = participants.findByToken(ann.getToken()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Mug");
        assertThat(prizes.findById(bike.getId()).orElseThrow().isClaimed()).isFalse();
    }

    @Test
    void deletingAParticipantReleasesTheirClaims() {
        Participant ann = participantService.save(participant("Ann", 1, 10));
        Prize bike = prizeService.save(prize("Bike"));
        prizeService.claim(bike, ann);

        participantService.delete(ann);
        em.flush();
        em.clear();

        assertThat(participants.findByToken(ann.getToken())).isEmpty();
        Prize reloaded = prizes.findById(bike.getId()).orElseThrow();
        assertThat(reloaded.isClaimed()).isFalse();
    }

    private Participant participant(String name, long start, long end) {
        Participant p = new Participant();
        p.setEvent(event);
        p.setName(name);
        p.addRange(start, end);
        p.setPhone("555-0100");
        return p;
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
}
