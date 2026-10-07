package org.openraffle.bgg;

import java.util.List;
import java.util.Optional;

/**
 * What the app needs from BoardGameGeek. Every call is best-effort: when BGG is slow,
 * down or not configured, searches come back empty and lookups absent rather than failing
 * the page that asked.
 */
public interface BggClient {

    /** False when no API token is configured; the UI then hides the BGG field. */
    boolean isEnabled();

    /** Games and expansions matching the query, best matches first. */
    List<BggItem> search(String query);

    Optional<BggThing> thing(long id);

    /** Fetches the image at {@code url}, if it is reachable and really is an image. */
    Optional<BggImage> download(String url);
}
