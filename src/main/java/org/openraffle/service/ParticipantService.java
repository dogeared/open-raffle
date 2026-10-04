package org.openraffle.service;

import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.domain.TicketRange;
import org.openraffle.repository.ParticipantRepository;
import org.openraffle.repository.PrizeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class ParticipantService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParticipantRepository participants;
    private final PrizeRepository prizes;

    public ParticipantService(ParticipantRepository participants, PrizeRepository prizes) {
        this.participants = participants;
        this.prizes = prizes;
    }

    /** The event's participants alphabetically (case-insensitive), lowest ticket first among namesakes. */
    @Transactional(readOnly = true)
    public List<Participant> findAll(Event event) {
        return participants.findAllByEvent(event).stream()
                .sorted(Comparator.comparing(Participant::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(Participant::getFirstTicket))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Participant> findByToken(String token) {
        return participants.findByToken(token);
    }

    /** The participant holding a drawn ticket, given as printed ("42", "987-042", "4563-100-300"). */
    @Transactional(readOnly = true)
    public Optional<Participant> findByTicket(Event event, String printedTicket) {
        return TicketRange.TicketNumber.parse(printedTicket)
                .flatMap(t -> participants.findHolding(event, t.prefix(), t.sequence()).stream().findFirst());
    }

    @Transactional(readOnly = true)
    public Optional<Participant> findByTicket(Event event, long plainTicket) {
        return findByTicket(event, String.valueOf(plainTicket));
    }

    /**
     * Saves a participant after validating their ticket ranges: at least one, each in order,
     * none overlapping each other, and none overlapping another participant of the event.
     *
     * @throws TicketRangeConflictException if a range overlaps another participant's tickets
     */
    public Participant save(Participant participant) {
        if (participant.getEvent() == null) {
            throw new IllegalArgumentException("Participant must belong to an event");
        }
        if (participant.getPhone() == null || participant.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        if (!Participant.isPlausiblePhone(participant.getPhone())) {
            throw new IllegalArgumentException("Phone: " + Participant.PHONE_RULE);
        }
        List<TicketRange> ranges = participant.getRanges();
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("At least one ticket range is required");
        }
        for (TicketRange range : ranges) {
            if (!range.isValid()) {
                throw new IllegalArgumentException("Ticket range " + range.getStart() + " – " + range.getEnd()
                        + ": the last ticket must not be before the first");
            }
        }
        for (int i = 0; i < ranges.size(); i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                if (ranges.get(i).overlaps(ranges.get(j))) {
                    throw new IllegalArgumentException("Ticket ranges " + ranges.get(i).getLabel() + " and "
                            + ranges.get(j).getLabel() + " overlap each other");
                }
            }
        }
        Set<Participant> conflicts = new LinkedHashSet<>();
        for (TicketRange range : ranges) {
            conflicts.addAll(participants.findOverlapping(
                    participant.getEvent(), range.getPrefix(), range.getStart(), range.getEnd(), participant.getId()));
        }
        if (!conflicts.isEmpty()) {
            throw new TicketRangeConflictException(List.copyOf(conflicts));
        }
        if (participant.getToken() == null) {
            participant.setToken(newToken());
        }
        participant.mirrorFirstRangeIntoLegacyColumns();
        return participants.save(participant);
    }

    public void delete(Participant participant) {
        // Release any prizes they claimed during the draw so the FK doesn't block the delete.
        List<Prize> claimed = prizes.findAllByClaimedBy(participant);
        claimed.forEach(p -> {
            p.setClaimedBy(null);
            p.setClaimedAt(null);
        });
        prizes.saveAll(claimed);
        participants.delete(participant);
    }

    public Participant updateWishlist(String token, List<Prize> orderedPrizes) {
        Participant participant = participants.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Unknown participant token"));
        participant.getWishlist().clear();
        participant.getWishlist().addAll(orderedPrizes);
        participant.setWishlistUpdatedAt(Instant.now());
        return participants.save(participant);
    }

    /**
     * Appends a prize the organizer handed out during the draw to the participant's list
     * (at the bottom) if they had not picked it themselves, so the draw page shows it
     * among their preferences.
     */
    public Participant addToWishlist(Participant participant, Prize prize) {
        Participant p = participants.findById(participant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown participant"));
        if (!p.getWishlist().contains(prize)) {
            p.getWishlist().add(prize);
            p = participants.save(p);
        }
        return p;
    }

    /**
     * Takes a prize off a participant's list at an organizer's request. If the participant
     * had claimed that prize during the draw, the claim is released too.
     */
    public Participant removeFromWishlist(Participant participant, Prize prize) {
        Participant p = participants.findById(participant.getId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown participant"));
        p.getWishlist().remove(prize);
        prizes.findById(prize.getId()).filter(current -> current.isClaimedBy(p)).ifPresent(current -> {
            current.setClaimedBy(null);
            current.setClaimedAt(null);
            prizes.save(current);
        });
        return participants.save(p);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class TicketRangeConflictException extends RuntimeException {
        private final List<Participant> conflicts;

        public TicketRangeConflictException(List<Participant> conflicts) {
            super("Ticket range overlaps: " + conflicts.stream()
                    .map(p -> p.getName() + " (" + p.getTicketRangeLabel() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse(""));
            this.conflicts = conflicts;
        }

        public List<Participant> getConflicts() {
            return conflicts;
        }
    }
}
