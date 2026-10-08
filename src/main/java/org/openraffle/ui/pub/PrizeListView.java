package org.openraffle.ui.pub;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.openraffle.ui.AppVersion;
import org.openraffle.domain.Event;
import org.openraffle.domain.Prize;
import org.openraffle.service.EventService;
import org.openraffle.service.PrizeService;
import org.openraffle.ui.AppFooter;
import org.openraffle.ui.Paginator;
import org.openraffle.ui.PrizeThumbnail;

import java.util.List;

/**
 * The public prize list for an event, at {@code /e/<event-name-as-slug>}: what a participant
 * sees on their wishlist page, minus the picking. No login, and no ids in the URL.
 */
@Route("e/:slug")
@PageTitle("Prizes")
@AnonymousAllowed
public class PrizeListView extends VerticalLayout implements BeforeEnterObserver {

    private final EventService eventService;
    private final PrizeService prizeService;
    private final AppVersion version;

    private final Div list = new Div();
    private final Paginator<Prize> pages = new Paginator<>(this::renderPage);
    private List<Prize> prizes = List.of();

    public PrizeListView(EventService eventService, PrizeService prizeService, AppVersion version) {
        this.eventService = eventService;
        this.prizeService = prizeService;
        this.version = version;
        setMaxWidth("640px");
        addClassNames(LumoUtility.Margin.Horizontal.AUTO, LumoUtility.Padding.MEDIUM);
        list.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Gap.XSMALL);
        list.setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent enter) {
        String slug = enter.getRouteParameters().get("slug").orElse("");
        Event event = eventService.findActiveBySlug(slug).orElse(null);
        removeAll();
        if (event == null) {
            add(new H1("Hmm, we couldn't find that raffle"),
                    new Paragraph("Check the link, or ask the raffle organizer for the right one."),
                    new AppFooter(version));
            return;
        }
        prizes = prizeService.findAll(event);
        build(event);
    }

    private void build(Event event) {
        Span eventName = new Span(event.getName());
        eventName.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.TERTIARY,
                LumoUtility.TextTransform.UPPERCASE, LumoUtility.FontWeight.SEMIBOLD);
        Paragraph intro = new Paragraph("Everything up for grabs. Hold a ticket? Scan the QR code the organizer gave you to pick your favorites.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY);

        add(eventName, new H1("Prizes"), intro, new H3("Available prizes"), list, pages, new AppFooter(version));
        pages.setItems(prizes);
        pages.setVisible(!prizes.isEmpty());
    }

    private void renderPage(List<Prize> pageOfPrizes) {
        list.removeAll();
        if (pageOfPrizes.isEmpty()) {
            Span empty = new Span("No prizes have been announced yet.");
            empty.addClassNames(LumoUtility.TextColor.TERTIARY);
            list.add(empty);
        }
        for (Prize prize : pageOfPrizes) {
            list.add(row(prize));
        }
    }

    /** A prize line in the wishlist page's style, with a quiet "Claimed" tag once it is gone. */
    private static HorizontalLayout row(Prize prize) {
        Div label = new Div(new Span(prize.getName()));
        if (prize.getDescription() != null && !prize.getDescription().isBlank()) {
            Div desc = new Div(new Span(prize.getDescription()));
            desc.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            label.add(desc);
        }
        HorizontalLayout row = new HorizontalLayout(label);
        Image thumbnail = PrizeThumbnail.of(prize, "3rem");
        if (thumbnail != null) {
            row.addComponentAsFirst(thumbnail);
        }
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        row.expand(label);
        if (prize.isClaimed()) {
            Span claimed = new Span("Claimed");
            claimed.getElement().getThemeList().addAll(java.util.List.of("badge", "contrast"));
            row.add(claimed);
        }
        return row;
    }
}
