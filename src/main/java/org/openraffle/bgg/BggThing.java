package org.openraffle.bgg;

/**
 * The details of one BoardGameGeek item that the app cares about. {@code rating} is the
 * community's average rating (1–10), null when nobody has rated it yet; {@code ratings} is
 * how many people rated it.
 */
public record BggThing(long id, String name, Integer year, String imageUrl, String thumbnailUrl, Double rating, Integer ratings) {
}
