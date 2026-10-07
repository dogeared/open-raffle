package org.openraffle.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns a name into the form it takes in a public URL: lowercase, words joined by single
 * dashes, accents folded, everything else dropped. "Carnage & Fun 29" becomes
 * "carnage-fun-29". A name with no usable characters at all gives "".
 */
public final class Slug {

    private Slug() {
    }

    public static String of(String name) {
        if (name == null) {
            return "";
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return folded.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
