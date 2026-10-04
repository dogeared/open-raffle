package org.openraffle.domain;

import org.junit.jupiter.api.Test;
import org.openraffle.domain.TicketRange.TicketNumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketRangeTest {

    @Test
    void parsesPlainAndPrefixedTicketNumbers() {
        assertThat(TicketNumber.parse("42")).contains(new TicketNumber("", 42, 0));
        assertThat(TicketNumber.parse(" 987-001 ")).contains(new TicketNumber("987", 1, 3));
        assertThat(TicketNumber.parse("4563-100-300")).contains(new TicketNumber("4563-100", 300, 0));
        assertThat(TicketNumber.parse("A-7")).contains(new TicketNumber("A", 7, 0));
        for (String bad : new String[] {"", "abc", "987-", "-5", "987-1a", "12 34", null}) {
            assertThat(TicketNumber.parse(bad)).as(String.valueOf(bad)).isEmpty();
        }
    }

    @Test
    void rangesKeepTheRollsFormattingInLabels() {
        assertThat(TicketRange.of("1", "100").getLabel()).isEqualTo("1 – 100");
        assertThat(TicketRange.of("987-001", "987-100").getLabel()).isEqualTo("987-001 – 987-100");
        assertThat(TicketRange.of("4563-100-300", "4563-100-1000").getLabel()).isEqualTo("4563-100-300 – 4563-100-1000");
        assertThat(TicketRange.of("5", "5").getLabel()).isEqualTo("5");
        assertThat(TicketRange.of("987-001", "987-100").getStartLabel()).isEqualTo("987-001");
        assertThat(TicketRange.of("987-001", "987-100").getEndLabel()).isEqualTo("987-100");
        assertThat(TicketRange.of("5", "5").isSingle()).isTrue();
        assertThat(TicketRange.of("987-001", "987-100").getCount()).isEqualTo(100);
    }

    @Test
    void bothEndsMustShareThePrefixAndBeInOrder() {
        assertThatThrownBy(() -> TicketRange.of("987-001", "988-100")).hasMessageContaining("share the prefix");
        assertThatThrownBy(() -> TicketRange.of("987-100", "987-001")).hasMessageContaining("before the first");
        assertThatThrownBy(() -> TicketRange.of("987-x", "987-100")).hasMessageContaining("not a ticket number");
    }

    @Test
    void differentPrefixesNeverOverlapOrMatch() {
        TicketRange a = TicketRange.of("4563-100", "4563-200");
        TicketRange b = TicketRange.of("4564-100", "4564-200");
        TicketRange c = TicketRange.of("4563-150", "4563-160");
        TicketRange plain = TicketRange.of("100", "200");

        assertThat(a.overlaps(b)).isFalse();
        assertThat(a.overlaps(c)).isTrue();
        assertThat(a.overlaps(plain)).isFalse();
        assertThat(a.contains(TicketNumber.parse("4563-150").orElseThrow())).isTrue();
        assertThat(a.contains(TicketNumber.parse("4564-150").orElseThrow())).isFalse();
        assertThat(a.contains(TicketNumber.parse("150").orElseThrow())).isFalse();
        assertThat(plain.contains(150)).isTrue();
    }
}
