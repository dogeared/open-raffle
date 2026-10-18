package org.openraffle.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.PhoneNumbers;
import org.openraffle.domain.Prize;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.service.PrizeService;
import org.openraffle.ui.MainLayout;
import org.openraffle.ui.Paginator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Who took what: one row per participant who claimed a prize on the draw page, with every
 * prize they took in one cell. The Claimed by, Prizes and When headers sort the whole report
 * (not just the page); it opens sorted by who took them.
 */
@Route(value = "events/:eventId/reports", layout = MainLayout.class)
@PageTitle("Reports | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class ReportsView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    private static final Comparator<Prize> BY_NAME = Comparator.comparing(Prize::getName, String.CASE_INSENSITIVE_ORDER);

    /** A participant and the prizes they claimed, A to Z. */
    public record Claimant(Participant who, List<Prize> prizes) {

        /** "Apron, Bike, Mug" */
        public String prizeList() {
            return prizes.stream().map(Prize::getName).collect(Collectors.joining(", "));
        }

        /** The most recent claim; null only for rows whose claims predate timestamps. */
        public Instant latestClaim() {
            return prizes.stream().map(Prize::getClaimedAt).filter(t -> t != null).max(Comparator.naturalOrder()).orElse(null);
        }
    }

    private final PrizeService prizeService;
    private final EventService eventService;
    private final Grid<Claimant> grid = new Grid<>(Claimant.class, false);
    private final Paginator<Claimant> pages = new Paginator<>(grid::setItems);
    private final Span summary = new Span();
    private final Grid.Column<Claimant> byClaimant;
    private Event event;
    private List<Claimant> claimants = List.of();

    public ReportsView(PrizeService prizeService, EventService eventService) {
        this.prizeService = prizeService;
        this.eventService = eventService;
        setSizeFull();

        H2 heading = new H2("Claimed prizes");
        summary.addClassNames(LumoUtility.TextColor.SECONDARY);
        Paragraph intro = new Paragraph("Everyone who took a prize on the draw page, with what they took. "
                + "Click Claimed by, Prizes or When to change the order.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.Top.NONE);

        byClaimant = grid.addColumn(c -> c.who().getName()).setHeader("Claimed by").setKey("claimedBy").setWidth("7em").setFlexGrow(2)
                .setComparator(Comparator.comparing(c -> c.who().getName(), String.CASE_INSENSITIVE_ORDER));
        // One truncated line; the dialog has the full list with each prize's time.
        grid.addComponentColumn(c -> {
            Button open = new Button(c.prizeList(), e -> showPrizes(c));
            open.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            open.setTooltipText(c.prizeList());
            open.addClassNames("cell-summary");
            return open;
        }).setHeader("Prizes").setKey("prizes").setWidth("8em").setFlexGrow(3)
                .setComparator(Comparator.comparing(Claimant::prizeList, String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(c -> c.who().getTicketRangeLabel()).setHeader("Tickets").setKey("tickets").setWidth("7em").setFlexGrow(2);
        grid.addColumn(c -> c.who().getPhone() == null ? "—" : PhoneNumbers.format(c.who().getPhone()))
                .setHeader("Phone").setKey("phone").setWidth("7em").setFlexGrow(1);
        grid.addColumn(c -> c.latestClaim() == null ? "" : WHEN.format(c.latestClaim()))
                .setHeader("When").setKey("when").setWidth("7em").setFlexGrow(1)
                .setComparator(Comparator.comparing(Claimant::latestClaim, Comparator.nullsLast(Comparator.naturalOrder())));
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        // The grid only ever holds one page, so sorting is done here over the whole report.
        grid.addSortListener(e -> pages.setItems(sorted(claimants)));
        grid.sort(List.of(new GridSortOrder<>(byClaimant, SortDirection.ASCENDING)));

        add(heading, intro, summary, grid, pages);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent enter) {
        EventScopedView.resolve(enter, eventService).ifPresent(e -> {
            event = e;
            refresh();
        });
    }

    private void refresh() {
        List<Prize> claimed = prizeService.findClaimed(event);
        claimants = groupByClaimant(claimed);
        int total = prizeService.findAll(event).size();
        summary.setText(claimed.isEmpty()
                ? "No prizes have been claimed yet."
                : claimed.size() + " of " + total + " prizes claimed by " + claimants.size()
                        + (claimants.size() == 1 ? " participant." : " participants."));
        pages.setItems(sorted(claimants));
    }

    /** One claimant per participant, their prizes A to Z (case-insensitive). */
    static List<Claimant> groupByClaimant(List<Prize> claimed) {
        Map<Participant, List<Prize>> byParticipant = new LinkedHashMap<>();
        for (Prize prize : claimed) {
            byParticipant.computeIfAbsent(prize.getClaimedBy(), k -> new ArrayList<>()).add(prize);
        }
        return byParticipant.entrySet().stream()
                .map(e -> new Claimant(e.getKey(), e.getValue().stream().sorted(BY_NAME).toList()))
                .toList();
    }

    /**
     * The report in the grid's current sort order, with the claimant's name breaking ties
     * and the most recent claim after that.
     */
    private List<Claimant> sorted(List<Claimant> report) {
        Comparator<Claimant> order = byClaimant.getComparator(SortDirection.ASCENDING)
                .thenComparing(Claimant::latestClaim, Comparator.nullsLast(Comparator.reverseOrder()));
        for (GridSortOrder<Claimant> sort : grid.getSortOrder().reversed()) {
            order = sort.getSorted().getComparator(sort.getDirection()).thenComparing(order);
        }
        return report.stream().sorted(order).toList();
    }

    private void showPrizes(Claimant claimant) {
        Dialog dialog = new Dialog(claimant.who().getName() + "'s prizes");
        UnorderedList list = new UnorderedList();
        for (Prize prize : claimant.prizes()) {
            ListItem item = new ListItem(new Span(prize.getName()));
            if (prize.getClaimedAt() != null) {
                Span when = new Span(" — " + WHEN.format(prize.getClaimedAt()));
                when.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.TERTIARY);
                item.add(when);
            }
            list.add(item);
        }
        Paragraph hint = new Paragraph(claimant.prizes().size() == 1 ? "1 prize, " : claimant.prizes().size() + " prizes, ");
        hint.setText(hint.getText() + "A to Z, with when each was claimed.");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        dialog.add(list, hint);
        dialog.setMaxWidth("480px");
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }
}
