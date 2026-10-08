package org.openraffle.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import org.openraffle.domain.Prize;

import java.util.Locale;

/**
 * BoardGameGeek's community rating as the coloured square BGG itself shows: green from 8,
 * dark green from 7, blue from 5, red below. Links to the game's BGG page.
 */
public final class BggRatingBadge {

    private BggRatingBadge() {
    }

    /** Null when the prize has no BGG rating to show. */
    public static Anchor of(Prize prize) {
        if (prize.getBggRating() == null || prize.getBggUrl() == null) {
            return null;
        }
        Span value = new Span(format(prize.getBggRating()));
        Anchor badge = new Anchor(prize.getBggUrl(), value);
        badge.setTarget("_blank");
        badge.addClassNames("bgg-rating", "bgg-rating-" + tier(prize.getBggRating()));
        badge.getElement().setAttribute("title", "BoardGameGeek community rating " + format(prize.getBggRating()) + " of 10");
        badge.getElement().setAttribute("aria-label", "BoardGameGeek rating " + format(prize.getBggRating()) + " out of 10");
        return badge;
    }

    /** One decimal, like BGG: 7.09005 shows as 7.1. */
    public static String format(double rating) {
        return String.format(Locale.ROOT, "%.1f", rating);
    }

    /** The colour band, by BGG's own thresholds. */
    public static String tier(double rating) {
        if (rating >= 8) {
            return "great";
        }
        if (rating >= 7) {
            return "good";
        }
        if (rating >= 5) {
            return "ok";
        }
        return "poor";
    }
}
