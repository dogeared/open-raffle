package org.openraffle.ui.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.Event;
import org.openraffle.domain.PhoneNumbers;
import org.openraffle.domain.Prize;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.service.PrizeService;
import org.openraffle.ui.MainLayout;
import org.openraffle.ui.Paginator;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Who took which prize: every claim recorded on the draw page, most recent first. */
@Route(value = "events/:eventId/reports", layout = MainLayout.class)
@PageTitle("Reports | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class ReportsView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    private final PrizeService prizeService;
    private final EventService eventService;
    private final Grid<Prize> grid = new Grid<>(Prize.class, false);
    private final Paginator<Prize> pages = new Paginator<>(grid::setItems);
    private final Span summary = new Span();
    private Event event;

    public ReportsView(PrizeService prizeService, EventService eventService) {
        this.prizeService = prizeService;
        this.eventService = eventService;
        setSizeFull();

        H2 heading = new H2("Claimed prizes");
        summary.addClassNames(LumoUtility.TextColor.SECONDARY);
        Paragraph intro = new Paragraph("Every prize handed out on the draw page, most recent first.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.Top.NONE);

        grid.addColumn(Prize::getName).setHeader("Prize").setKey("prize").setWidth("8em").setFlexGrow(3);
        grid.addColumn(p -> p.getClaimedBy().getName()).setHeader("Claimed by").setKey("claimedBy").setWidth("7em").setFlexGrow(2);
        grid.addColumn(p -> p.getClaimedBy().getTicketRangeLabel()).setHeader("Tickets").setKey("tickets").setWidth("7em").setFlexGrow(2);
        grid.addColumn(p -> p.getClaimedBy().getPhone() == null ? "—" : PhoneNumbers.format(p.getClaimedBy().getPhone()))
                .setHeader("Phone").setKey("phone").setWidth("7em").setFlexGrow(1);
        grid.addColumn(p -> p.getClaimedAt() == null ? "" : WHEN.format(p.getClaimedAt()))
                .setHeader("When").setKey("when").setWidth("7em").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();

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
        int total = prizeService.findAll(event).size();
        summary.setText(claimed.isEmpty()
                ? "No prizes have been claimed yet."
                : claimed.size() + " of " + total + " prizes claimed.");
        pages.setItems(claimed);
    }
}
