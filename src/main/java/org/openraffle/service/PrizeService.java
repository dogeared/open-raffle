package org.openraffle.service;

import org.openraffle.bgg.BggClient;
import org.openraffle.bgg.BggImage;
import org.openraffle.bgg.BggThing;
import org.openraffle.domain.Event;
import org.openraffle.image.PrizeImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.repository.PrizeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PrizeService {

    private static final Logger log = LoggerFactory.getLogger(PrizeService.class);

    private final PrizeRepository prizes;
    private final BggClient bgg;
    private final PrizeImageStore images;

    public PrizeService(PrizeRepository prizes, BggClient bgg, PrizeImageStore images) {
        this.prizes = prizes;
        this.bgg = bgg;
        this.images = images;
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

    /**
     * Saves the prize and keeps its picture in step with its BoardGameGeek link: a newly
     * linked (or re-linked) game gets its box image downloaded once and stored locally; an
     * unlinked prize loses the picture. BGG being unreachable never fails the save; the
     * image is fetched on the next save instead.
     */
    public Prize save(Prize prize) {
        if (prize.getEvent() == null) {
            throw new IllegalArgumentException("Prize must belong to an event");
        }
        Prize saved = prizes.save(prize);
        if (saved.getBggId() == null) {
            if (saved.hasImage() || saved.getBggImageId() != null) {
                images.delete(saved.getImageFile());
                saved.setImageFile(null);
                saved.setBggImageId(null);
                saved.setBggName(null);
                saved = prizes.save(saved);
            }
        } else if (!saved.getBggId().equals(saved.getBggImageId())) {
            fetchImage(saved);
            saved = prizes.save(saved);
        } else if (ratingIsStale(saved)) {
            Prize linked = saved;
            bgg.thing(linked.getBggId()).ifPresent(thing -> recordRating(linked, thing));
            saved = prizes.save(linked);
        }
        return saved;
    }

    /** How long a cached BGG rating is trusted before it is refreshed. */
    public static final Duration RATING_MAX_AGE = Duration.ofDays(30);

    /** Linked prizes whose rating was never fetched (linked before ratings existed, or BGG was down) or is a month old. */
    @Transactional(readOnly = true)
    public List<Prize> findWithStaleRating() {
        return prizes.findAllWithStaleRating(Instant.now().minus(RATING_MAX_AGE));
    }

    /** Fetches the prize's BGG rating now. Returns false when BGG did not answer. */
    public boolean refreshRating(Prize prize) {
        if (prize.getBggId() == null) {
            return false;
        }
        Optional<BggThing> thing = bgg.thing(prize.getBggId());
        if (thing.isEmpty()) {
            return false;
        }
        recordRating(prize, thing.get());
        prizes.save(prize);
        return true;
    }

    private static boolean ratingIsStale(Prize prize) {
        return prize.getBggRatingAt() == null || prize.getBggRatingAt().isBefore(Instant.now().minus(RATING_MAX_AGE));
    }

    private static void recordRating(Prize prize, BggThing thing) {
        prize.setBggRating(thing.rating());
        prize.setBggRatingAt(Instant.now());
    }

    private void fetchImage(Prize prize) {
        long bggId = prize.getBggId();
        Optional<BggThing> thing = bgg.thing(bggId);
        if (thing.isEmpty()) {
            log.warn("BGG item {} for prize {} could not be read; will retry on the next save", bggId, prize.getId());
            return;
        }
        if (prize.getBggName() == null || prize.getBggName().isBlank()) {
            prize.setBggName(thing.get().name());
        }
        recordRating(prize, thing.get());
        String previous = prize.getImageFile();
        Optional<BggImage> image = downloadArt(thing.get());
        if (image.isEmpty()) {
            // A game without box art is still linked; just nothing to show.
            prize.setImageFile(null);
            prize.setBggImageId(bggId);
        } else {
            try {
                prize.setImageFile(images.store(prize.getId(), image.get().bytes(), image.get().extension()));
                prize.setBggImageId(bggId);
            } catch (IOException e) {
                log.warn("Could not store the image for prize {}: {}", prize.getId(), e.getMessage());
                return;
            }
        }
        if (previous != null) {
            images.delete(previous);
        }
    }

    /** The full box image, or BGG's smaller thumbnail when the original is unusable (too big, say). */
    private Optional<BggImage> downloadArt(BggThing thing) {
        Optional<BggImage> image = bgg.download(thing.imageUrl());
        if (image.isEmpty() && thing.thumbnailUrl() != null && !thing.thumbnailUrl().equals(thing.imageUrl())) {
            image = bgg.download(thing.thumbnailUrl());
        }
        return image;
    }

    /**
     * Brings back a picture whose file has gone missing (the host's filesystem was reset)
     * by fetching it from BoardGameGeek again under the same name, so links and caches
     * keep working. Empty when no prize uses that name, it has no BGG link, or BGG is down.
     */
    @Transactional(readOnly = true)
    public Optional<Path> restoreImage(String imageFile) {
        if (imageFile == null || !PrizeImageStore.FILE_NAME.matcher(imageFile).matches()) {
            return Optional.empty();
        }
        return prizes.findByImageFile(imageFile)
                .filter(prize -> prize.getBggId() != null)
                .flatMap(prize -> bgg.thing(prize.getBggId()))
                .flatMap(this::downloadArt)
                .flatMap(image -> {
                    try {
                        log.info("Restored prize image {} from BoardGameGeek", imageFile);
                        return images.storeAs(imageFile, image.bytes());
                    } catch (IOException e) {
                        log.warn("Could not restore prize image {}: {}", imageFile, e.getMessage());
                        return Optional.empty();
                    }
                });
    }

    public void delete(Prize prize) {
        prizes.delete(prize);
        if (prize.hasImage()) {
            images.delete(prize.getImageFile());
        }
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
