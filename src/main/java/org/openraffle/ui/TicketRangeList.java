package org.openraffle.ui;

import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import org.openraffle.domain.Participant;
import org.openraffle.domain.TicketRange;

import java.util.List;

/**
 * A participant's tickets as a bulleted list, one range per line, styled like
 * {@link TicketRangeLabel}. Several prefixed ranges in a sentence wrap into a mess; a list
 * keeps each range whole and easy to scan.
 */
public class TicketRangeList extends UnorderedList {

    public TicketRangeList(Participant participant) {
        this(participant.getRangesInOrder());
    }

    public TicketRangeList(List<TicketRange> ranges) {
        addClassName("ticket-range-list");
        ranges.forEach(range -> add(new ListItem(TicketRangeLabel.range(range))));
    }
}
