package org.openraffle.domain;

import java.util.regex.Pattern;

/**
 * Presents phone numbers the way people expect to read them. US and Canadian numbers (the
 * North American Numbering Plan) become "(aaa) bbb-cccc"; anything else is shown exactly as
 * it was entered, because other countries' grouping rules are too varied to guess.
 */
public final class PhoneNumbers {

    /**
     * Ten digits whose area code does not start with 0 or 1 (the plan has none), which also
     * keeps a UK-style "020 7946 0958" typed without its + from being dressed up as American.
     */
    private static final Pattern NANP = Pattern.compile("([2-9]\\d{2})(\\d{3})(\\d{4})");

    private PhoneNumbers() {
    }

    /**
     * "(aaa) bbb-cccc" for a ten-digit number with no country code, or one led by the US
     * country code 1 (with or without a +). Anything else — another country code, too few
     * or too many digits, an area code that cannot exist — comes back unchanged, and so does
     * null.
     */
    public static String format(String phone) {
        if (phone == null) {
            return null;
        }
        String raw = phone.trim();
        boolean plus = raw.startsWith("+");
        String digits = raw.replaceAll("\\D", "");
        if (plus && !digits.startsWith("1")) {
            return raw; // another country's number: leave its own grouping alone
        }
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        var m = NANP.matcher(digits);
        return m.matches() ? "(" + m.group(1) + ") " + m.group(2) + "-" + m.group(3) : raw;
    }

    /** What a tel: link should dial: the digits, keeping a leading +. */
    public static String dialable(String phone) {
        return phone == null ? null : phone.trim().replaceAll("[^+\\d]", "");
    }
}
