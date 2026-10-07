package org.openraffle.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Prize images on the filesystem, under {@code raffle.images-dir}. File names are minted
 * here, never taken from a request: {@code prize-<prize id>-<16 hex>.<jpg|png|gif|webp>},
 * so a name that does not match that shape is simply not one of ours.
 */
@Component
public class PrizeImageStore {

    private static final Logger log = LoggerFactory.getLogger(PrizeImageStore.class);
    public static final Pattern FILE_NAME = Pattern.compile("prize-(\\d{1,18})-([a-f0-9]{16})\\.(jpg|png|gif|webp)");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path dir;

    public PrizeImageStore(@Value("${raffle.images-dir:./data/images}") String imagesDir) {
        this.dir = Path.of(imagesDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Prize images directory {} cannot be created; prize images are off: {}", dir, e.getMessage());
        }
    }

    public Path directory() {
        return dir;
    }

    /** Writes the bytes under a fresh name and returns that name. */
    public String store(long prizeId, byte[] bytes, String extension) throws IOException {
        if (!isImageExtension(extension)) {
            throw new IllegalArgumentException("Not an image extension: " + extension);
        }
        byte[] salt = new byte[8];
        RANDOM.nextBytes(salt);
        String name = "prize-" + prizeId + "-" + HexFormat.of().formatHex(salt) + "." + extension;
        Path target = dir.resolve(name);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return name;
    }

    /**
     * Re-creates a file under a name this store minted earlier (the prize still refers to
     * it) after the file was lost, e.g. on a host whose filesystem does not survive a
     * deploy. A second writer losing the race is fine: the bytes are the same picture.
     */
    public Optional<Path> storeAs(String name, byte[] bytes) throws IOException {
        if (name == null || !FILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a prize image name: " + name);
        }
        Path target = dir.resolve(name).normalize();
        if (!target.getParent().equals(dir)) {
            throw new IllegalArgumentException("Not a prize image name: " + name);
        }
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            // someone else restored it first
        }
        return resolve(name);
    }

    /** The file for a stored image name, if the name is one of ours and the file exists. */
    public Optional<Path> resolve(String name) {
        if (name == null || !FILE_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path file = dir.resolve(name).normalize();
        if (!file.getParent().equals(dir) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    public void delete(String name) {
        resolve(name).ifPresent(file -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Could not delete prize image {}: {}", file, e.getMessage());
            }
        });
    }

    public static String contentType(String name) {
        return switch (extensionOf(name)) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    static boolean isImageExtension(String extension) {
        return "jpg".equals(extension) || "png".equals(extension) || "gif".equals(extension) || "webp".equals(extension);
    }
}
