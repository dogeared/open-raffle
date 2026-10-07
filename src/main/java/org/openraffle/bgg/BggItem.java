package org.openraffle.bgg;

/** One BoardGameGeek search hit: enough to pick a game from a type-ahead list. */
public record BggItem(long id, String name, Integer year) {

    /** "Catan (1995)", or just the name when the year is unknown. */
    public String label() {
        return year == null ? name : name + " (" + year + ")";
    }

    public String url() {
        return "https://boardgamegeek.com/boardgame/" + id;
    }
}
