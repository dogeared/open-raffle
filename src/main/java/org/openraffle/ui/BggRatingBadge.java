package org.openraffle.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import org.openraffle.domain.Prize;

import java.util.Locale;

/**
 * BoardGameGeek's community rating as the coloured square a BGG game page shows: the band
 * is the whole number of the shown score ("1" … "10", or "unranked" for a game too few
 * people have rated), and the stylesheet gives each band the colour BGG's game pages use
 * (deep green for 9–10, green for 8, blue for 7, slate blue for 5–6, reds below, grey when
 * unranked). Links to the game's BGG page.
 */
public final class BggRatingBadge {

    /** Below this many ratings BGG leaves a game unranked and shows its score in grey. */
    public static final int RANKED_FROM = 30;

    private BggRatingBadge() {
    }

    /** Null when the prize has no BGG rating to show. */
    public static Anchor of(Prize prize) {
        if (prize.getBggRating() == null || prize.getBggUrl() == null) {
            return null;
        }
        String shown = format(prize.getBggRating());
        Span value = new Span(shown);
        Anchor badge = new Anchor(prize.getBggUrl(), value);
        badge.setTarget("_blank");
        badge.addClassNames("bgg-rating", "bgg-rating-" + tier(prize.getBggRating(), prize.getBggRatingCount()));
        String count = prize.getBggRatingCount() == null ? "" : " from " + prize.getBggRatingCount() + " ratings";
        badge.getElement().setAttribute("title", "BoardGameGeek community rating " + shown + " of 10" + count);
        badge.getElement().setAttribute("aria-label", "BoardGameGeek rating " + shown + " out of 10");
        return badge;
    }

    /** One decimal, like BGG: 7.09005 shows as 7.1. */
    public static String format(double rating) {
        return String.format(Locale.ROOT, "%.1f", rating);
    }

    /**
     * The colour band: "unranked" with too few ratings, else the whole number of the
     * displayed score ("1" … "10"), so 7.96 shows "8.0" and is coloured as an 8.
     */
    public static String tier(double rating, Integer ratingCount) {
        if (ratingCount != null && ratingCount < RANKED_FROM) {
            return "unranked";
        }
        double shown = Math.round(rating * 10) / 10.0;
        int band = (int) Math.floor(shown);
        return String.valueOf(Math.max(1, Math.min(10, band)));
    }
}
