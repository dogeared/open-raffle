package org.openraffle.ui;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.openraffle.domain.Participant;
import org.openraffle.domain.TicketRange;

import java.util.List;

/**
 * A participant's tickets with each printed number set apart, e.g. [987-001] – [987-100], [5].
 *
 * <p>Ticket numbers can carry a dashed prefix, so in plain text the dash inside a number is
 * hard to tell from the dash between two numbers. Each number is its own {@value #NUMBER_CLASS}
 * span (styled in the theme); the range dash and the commas stay plain text. A range never
 * wraps, so a line can only break between ranges. For more than one range, prefer
 * {@link TicketRangeList}.
 * Screen readers tend to skip the dash, so it is hidden from them and they hear "to" instead.
 */
public class TicketRangeLabel extends Span {

    public static final String NUMBER_CLASS = "ticket-number";
    public static final String RANGE_CLASS = "ticket-range";

    public TicketRangeLabel(Participant participant) {
        this(participant.getRangesInOrder());
    }

    public TicketRangeLabel(List<TicketRange> ranges) {
        addClassName("ticket-range-label");
        for (int i = 0; i < ranges.size(); i++) {
            if (i > 0) {
                add(new Text(", "));
            }
            add(range(ranges.get(i)));
        }
    }

    /** One range, kept on one line so it never wraps at its own dash. */
    static Span range(TicketRange range) {
        Span span = new Span(number(range.getStartLabel()));
        span.addClassName(RANGE_CLASS);
        if (!range.isSingle()) {
            span.add(new Text(" "), dash(), new Text(" "), number(range.getEndLabel()));
        }
        return span;
    }

    /** "–" on screen, "to" for screen readers. */
    private static Span dash() {
        Span shown = new Span("–");
        shown.getElement().setAttribute("aria-hidden", "true");
        Span spoken = new Span("to");
        spoken.addClassName(LumoUtility.Accessibility.SCREEN_READER_ONLY);
        return new Span(shown, spoken);
    }

    private static Span number(String printed) {
        Span number = new Span(printed);
        number.addClassName(NUMBER_CLASS);
        return number;
    }
}
