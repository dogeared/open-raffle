package org.openraffle.service;

import org.openraffle.bgg.BggClient;
import org.openraffle.bgg.BggImage;
import org.openraffle.bgg.BggThing;
import org.openraffle.domain.Event;
import org.openraffle.domain.PrizePicture;
import org.openraffle.drive.DriveException;
import org.openraffle.drive.DriveService;
import org.openraffle.image.InvalidImageException;
import org.openraffle.image.UploadGate;
import org.openraffle.image.UploadRateLimiter;
import org.openraffle.image.UploadedImage;
import org.openraffle.repository.PrizePictureRepository;
import org.openraffle.security.CurrentUser;
import org.openraffle.image.PrizeImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
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
    private final PrizePictureRepository pictures;
    private final BggClient bgg;
    private final PrizeImageStore images;
    private final DriveService drive;
    private final EventService eventService;
    private final CurrentUser currentUser;
    private final UploadRateLimiter uploadLimit;
    private final UploadGate uploadGate;

    public PrizeService(PrizeRepository prizes, PrizePictureRepository pictures, BggClient bgg, PrizeImageStore images,
                        DriveService drive, EventService eventService, CurrentUser currentUser, UploadRateLimiter uploadLimit,
                        UploadGate uploadGate) {
        this.prizes = prizes;
        this.pictures = pictures;
        this.bgg = bgg;
        this.images = images;
        this.drive = drive;
        this.eventService = eventService;
        this.currentUser = currentUser;
        this.uploadLimit = uploadLimit;
        this.uploadGate = uploadGate;
    }

    /** The event's prizes in alphabetical order; participants rank them themselves. */
    @Transactional(readOnly = true)
    public List<Prize> findAll(Event event) {
        return prizes.findAllByEventAlphabetically(event);
    }

    @Transactional(readOnly = true)
    public Optional<Prize> findById(Long id) {
        return id == null ? Optional.empty() : prizes.findById(id);
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

    /**
     * How many missing pictures may be fetched back (from Drive or BGG) at the same time.
     * After a restart the cache is empty and a page of thumbnails asks for them all at
     * once; each fetch holds a whole file in memory, so the rest wait their turn briefly
     * and give up (a 404 the browser retries later) rather than pile up.
     */
    static final int CONCURRENT_RESTORES = 2;
    private static final java.util.concurrent.Semaphore restores = new java.util.concurrent.Semaphore(CONCURRENT_RESTORES, true);

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
        prize.setBggRatingCount(thing.ratings());
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
        boolean turn;
        try {
            turn = restores.tryAcquire(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (!turn) {
            log.info("Too many pictures being fetched back at once; {} will be asked for again", imageFile);
            return Optional.empty();
        }
        try {
            // Another request may have restored it while this one waited.
            Optional<Path> already = images.resolve(imageFile);
            if (already.isPresent()) {
                return already;
            }
            Optional<PrizePicture> uploaded = pictures.findByFileName(imageFile);
            if (uploaded.isPresent()) {
                return restoreUpload(uploaded.get());
            }
            return prizes.findByImageFile(imageFile).flatMap(this::restoreBggImage);
        } finally {
            restores.release();
        }
    }

    /** An organizer's picture back from Google Drive; failing that, the prize's BGG box image. */
    private Optional<Path> restoreUpload(PrizePicture picture) {
        Optional<Path> fromDrive = drive.download(picture.getDriveFileId()).flatMap(bytes -> {
            try {
                log.info("Restored prize picture {} from Google Drive", picture.getFileName());
                return images.storeAs(picture.getFileName(), bytes);
            } catch (IOException e) {
                log.warn("Could not restore prize picture {}: {}", picture.getFileName(), e.getMessage());
                return Optional.empty();
            }
        });
        if (fromDrive.isPresent()) {
            return fromDrive;
        }
        Prize prize = picture.getPrize();
        if (prize == null || !prize.hasBggImage()) {
            return Optional.empty();
        }
        log.info("Prize picture {} unavailable; falling back to the BGG image", picture.getFileName());
        return images.resolve(prize.getImageFile()).or(() -> restoreBggImage(prize));
    }

    private Optional<Path> restoreBggImage(Prize prize) {
        if (prize.getBggId() == null || prize.getImageFile() == null) {
            return Optional.empty();
        }
        return bgg.thing(prize.getBggId())
                .flatMap(this::downloadArt)
                .flatMap(image -> {
                    try {
                        log.info("Restored prize image {} from BoardGameGeek", prize.getImageFile());
                        return images.storeAs(prize.getImageFile(), image.bytes());
                    } catch (IOException e) {
                        log.warn("Could not restore prize image {}: {}", prize.getImageFile(), e.getMessage());
                        return Optional.empty();
                    }
                });
    }

    public void delete(Prize prize) {
        Prize current = prizes.findById(prize.getId()).orElse(prize);
        prizes.delete(current);
        if (current.hasBggImage()) {
            images.delete(current.getImageFile());
        }
        for (PrizePicture picture : current.getPictures()) {
            images.delete(picture.getFileName());
            drive.delete(picture.getDriveFileId());
        }
    }

    // --- organizers' own pictures ---------------------------------------------------------

    /**
     * Adds an uploaded picture to the end of the prize's list, after every check: the user
     * may run the event, the prize has room, the user is not flooding, Google Drive is
     * connected and working, and the bytes really are a picture (which is re-encoded, see
     * {@link UploadedImage}). The picture goes to Drive first and the local cache second;
     * if Drive refuses, nothing is kept.
     *
     * @throws InvalidImageException for anything the organizer can fix (type, size, count)
     * @throws DriveException        when Google Drive is not available
     */
    public PrizePicture addPicture(Prize prize, byte[] upload, String declaredContentType, String fileName)
            throws InvalidImageException, DriveException {
        try {
            Path temp = Files.createTempFile("open-raffle-upload-", ".bin");
            try {
                Files.write(temp, upload == null ? new byte[0] : upload);
                return addPicture(prize, temp, declaredContentType, fileName);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new InvalidImageException("The picture could not be read.");
        }
    }

    /**
     * The same, for an upload already streamed to {@code file} (which the caller deletes).
     * Pictures are processed a few at a time ({@link UploadGate}): the memory-hungry part is
     * decoding, and a batch of six phone photos at once is more than a small server has.
     */
    public PrizePicture addPicture(Prize prize, Path file, String declaredContentType, String fileName)
            throws InvalidImageException, DriveException {
        Prize current = requireEditable(prize);
        if (current.getPictures().size() >= Prize.MAX_PICTURES) {
            throw new InvalidImageException("A prize can have at most " + Prize.MAX_PICTURES + " pictures.");
        }
        String user = currentUser.email().orElse("");
        if (!uploadLimit.allow(user)) {
            throw new InvalidImageException("Too many uploads in a short time; please wait a few minutes.");
        }
        if (!drive.canUpload()) {
            throw new DriveException("Pictures cannot be uploaded until Google Drive is connected (Settings).");
        }
        if (!uploadGate.enter()) {
            throw new InvalidImageException("The server is busy with other pictures; please try this one again.");
        }
        String name;
        String driveFileId;
        String contentType;
        try {
            UploadedImage.Processed processed = UploadedImage.process(file, declaredContentType, fileName);
            contentType = processed.contentType();
            try {
                name = images.store(current.getId(), processed.bytes(), processed.extension());
            } catch (IOException e) {
                throw new InvalidImageException("The picture could not be stored on the server.");
            }
            try {
                driveFileId = drive.upload(current.getEvent(), name, processed.contentType(), processed.bytes());
            } catch (DriveException e) {
                images.delete(name);
                throw e;
            }
        } finally {
            uploadGate.leave();
        }
        PrizePicture picture = new PrizePicture();
        picture.setPrize(current);
        picture.setPosition(current.getPictures().size());
        picture.setFileName(name);
        picture.setDriveFileId(driveFileId);
        picture.setContentType(contentType);
        picture.setCreatedAt(Instant.now());
        picture.setUploadedBy(user.isEmpty() ? null : user);
        current.getPictures().add(picture);
        prizes.save(current);
        return pictures.findByFileName(name).orElse(picture);
    }

    /** Removes the picture from the prize, the local cache and (best effort) Drive. */
    public void removePicture(Prize prize, PrizePicture picture) {
        Prize current = requireEditable(prize);
        boolean removed = current.getPictures().removeIf(p -> p.getId() != null && p.getId().equals(picture.getId()));
        if (!removed) {
            return;
        }
        renumber(current);
        prizes.save(current);
        images.delete(picture.getFileName());
        drive.delete(picture.getDriveFileId());
    }

    /** Moves the picture {@code delta} places (negative = towards the front); the first is the primary. */
    public void movePicture(Prize prize, PrizePicture picture, int delta) {
        Prize current = requireEditable(prize);
        List<PrizePicture> list = current.getPictures();
        int from = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() != null && list.get(i).getId().equals(picture.getId())) {
                from = i;
            }
        }
        if (from < 0) {
            return;
        }
        int to = Math.max(0, Math.min(list.size() - 1, from + delta));
        PrizePicture moving = list.remove(from);
        list.add(to, moving);
        renumber(current);
        prizes.save(current);
    }

    private static void renumber(Prize prize) {
        List<PrizePicture> list = prize.getPictures();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPosition(i);
        }
    }

    /** The prize as stored, after checking the current user may run its event. */
    private Prize requireEditable(Prize prize) {
        Prize current = prizes.findById(prize.getId())
                .orElseThrow(() -> new IllegalArgumentException("The prize no longer exists"));
        eventService.requireAccess(current.getEvent().getId());
        return current;
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
