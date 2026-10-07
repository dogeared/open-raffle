package org.openraffle.bgg;

/** A downloaded image, already checked to really be one; {@code extension} is jpg, png, gif or webp. */
public record BggImage(byte[] bytes, String extension) {
}
