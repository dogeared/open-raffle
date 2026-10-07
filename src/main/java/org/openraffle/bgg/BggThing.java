package org.openraffle.bgg;

/** The details of one BoardGameGeek item that the app cares about. */
public record BggThing(long id, String name, Integer year, String imageUrl, String thumbnailUrl) {
}
