package org.openraffle.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.TicketRange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TicketRangeLabelTest {

    /** The printed numbers, in order, wherever they sit in the component tree. */
    static List<String> numbers(Component root) {
        List<String> found = new ArrayList<>();
        if (root instanceof Span s && s.hasClassName(TicketRangeLabel.NUMBER_CLASS)) {
            found.add(s.getText());
        }
        root.getChildren().forEach(child -> found.addAll(numbers(child)));
        return found;
    }

    /** Text on screen: everything but screen-reader-only spans. */
    static String shown(Component component) {
        return text(component.getElement(), e -> e.getClassList().contains(LumoUtility.Accessibility.SCREEN_READER_ONLY));
    }


    /** Text a screen reader announces: everything but aria-hidden spans. */
    static String spoken(Component label) {
        return text(label.getElement(), e -> "true".equals(e.getAttribute("aria-hidden")));
    }

    private static String text(Element element, Predicate<Element> skip) {
        if (element.isTextNode()) {
            return element.getText();
        }
        if (skip.test(element)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        element.getChildren().forEach(child -> text.append(text(child, skip)));
        return text.toString();
    }

    @Test
    void eachPrintedNumberIsItsOwnChip() {
        TicketRangeLabel label = new TicketRangeLabel(List.of(TicketRange.of("987-001", "987-100")));

        assertThat(numbers(label)).containsExactly("987-001", "987-100");
        assertThat(shown(label)).isEqualTo("987-001 – 987-100");
    }

    @Test
    void screenReadersHearToInsteadOfTheDash() {
        TicketRangeLabel label = new TicketRangeLabel(List.of(TicketRange.of("987-001", "987-100")));

        assertThat(spoken(label)).isEqualTo("987-001 to 987-100");
    }

    @Test
    void aSingleTicketIsOneChip() {
        TicketRangeLabel label = new TicketRangeLabel(List.of(TicketRange.of("12-05", "12-05")));

        assertThat(numbers(label)).containsExactly("12-05");
        assertThat(shown(label)).isEqualTo("12-05");
        assertThat(spoken(label)).isEqualTo("12-05");
    }

    @Test
    void severalRangesAreSeparatedByCommas() {
        TicketRangeLabel label = new TicketRangeLabel(List.of(
                TicketRange.of("1", "10"), TicketRange.of("42", "42"), TicketRange.of("4563-100-300", "4563-100-1000")));

        assertThat(numbers(label)).containsExactly("1", "10", "42", "4563-100-300", "4563-100-1000");
        assertThat(shown(label)).isEqualTo("1 – 10, 42, 4563-100-300 – 4563-100-1000");
        assertThat(spoken(label)).isEqualTo("1 to 10, 42, 4563-100-300 to 4563-100-1000");
    }

    @Test
    void aRangeNeverWrapsAtItsOwnDash() {
        TicketRangeLabel label = new TicketRangeLabel(List.of(TicketRange.of("1", "10"), TicketRange.of("42", "42")));

        assertThat(label.getChildren().filter(c -> c instanceof Span s && s.hasClassName(TicketRangeLabel.RANGE_CLASS)))
                .hasSize(2);
    }

    @Test
    void aListHasOneRangePerItem() {
        TicketRangeList list = new TicketRangeList(List.of(
                TicketRange.of("98799-369", "98799-379"), TicketRange.of("98799-400", "98799-400")));

        List<ListItem> items = list.getChildren().map(ListItem.class::cast).toList();
        assertThat(items).hasSize(2);
        assertThat(shown(items.get(0))).isEqualTo("98799-369 – 98799-379");
        assertThat(shown(items.get(1))).isEqualTo("98799-400");
        assertThat(numbers(list)).containsExactly("98799-369", "98799-379", "98799-400");
    }
}
