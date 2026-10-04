package org.openraffle.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "5551234567,        (555) 123-4567",
            "555-123-4567,      (555) 123-4567",
            "555.123.4567,      (555) 123-4567",
            "(555) 123 4567,    (555) 123-4567",
            "1-555-123-4567,    (555) 123-4567",
            "1 (555) 123-4567,  (555) 123-4567",
            "+1 555 123 4567,   (555) 123-4567",
            "+15551234567,      (555) 123-4567",
            "' 5551234567 ',    (555) 123-4567",
    })
    void usNumbersGetTheFamiliarGrouping(String raw, String expected) {
        assertThat(PhoneNumbers.format(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "+44 20 7946 0958",   // another country: its own grouping stays
            "+1 020 123 4567",    // area code cannot start with 0
            "555-0100",           // seven digits: no area code to show
            "020 7946 0958",      // UK number typed without its +: a 0 area code does not exist here
            "15551234567890",     // too long
            "+1",                 // nothing to format
    })
    void anythingElseIsLeftAsEntered(String raw) {
        assertThat(PhoneNumbers.format(raw)).isEqualTo(raw);
    }

    @Test
    void nullStaysNull() {
        assertThat(PhoneNumbers.format(null)).isNull();
        assertThat(PhoneNumbers.dialable(null)).isNull();
    }

    @Test
    void dialableKeepsOnlyDigitsAndALeadingPlus() {
        assertThat(PhoneNumbers.dialable("(555) 123-4567")).isEqualTo("5551234567");
        assertThat(PhoneNumbers.dialable("+44 20 7946 0958")).isEqualTo("+442079460958");
    }
}
