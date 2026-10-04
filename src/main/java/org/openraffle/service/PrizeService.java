package org.openraffle.service;

import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.repository.PrizeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PrizeService {

    private final PrizeRepository prizes;

    public PrizeService(PrizeRepository prizes) {
        this.prizes = prizes;
    }

    /** The event's prizes in alphabetical order; participants rank them themselves. */
    @Transactional(readOnly = true)
    public List<Prize> findAll(Event event) {
        return prizes.findAllByEventAlphabetically(event);
    }

    /** The event's prizes that have been handed out, most recent first. */
    @Transactional(readOnly = true)
    public List<Prize> findClaimed(Event event) {
        return prizes.findAllByEventAndClaimedByIsNotNullOrderByClaimedAtDesc(event);
    }

    public Prize save(Prize prize) {
        if (prize.getEvent() == null) {
            throw new IllegalArgumentException("Prize must belong to an event");
        }
        return prizes.save(prize);
    }

    public void delete(Prize prize) {
        prizes.delete(prize);
    }

    /** Records that {@code winner} took the prize. Fails if someone else already has it. */
    public Prize claim(Prize prize, Participant winner) {
        Prize current = prizes.findById(prize.getId()).orElseThrow();
        if (current.isClaimed() && !current.isClaimedBy(winner)) {
            throw new IllegalStateException(current.getName() + " was already claimed by " + current.getClaimedBy().getName());
        }
        current.setClaimedBy(winner);
        current.setClaimedAt(Instant.now());
        return prizes.save(current);
    }

    public Prize unclaim(Prize prize) {
        Prize current = prizes.findById(prize.getId()).orElseThrow();
        current.setClaimedBy(null);
        current.setClaimedAt(null);
        return prizes.save(current);
    }

    /** Called before a participant is deleted so their claims don't dangle. */
    public void releaseClaimsOf(Participant participant) {
        List<Prize> claimed = prizes.findAllByClaimedBy(participant);
        claimed.forEach(p -> {
            p.setClaimedBy(null);
            p.setClaimedAt(null);
        });
        prizes.saveAll(claimed);
    }
}
