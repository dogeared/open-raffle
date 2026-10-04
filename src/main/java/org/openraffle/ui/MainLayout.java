package org.openraffle.ui;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.Event;
import org.openraffle.security.CurrentUser;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.ui.admin.DrawView;
import org.openraffle.ui.admin.ParticipantsView;
import org.openraffle.ui.admin.PrizesView;
import org.openraffle.ui.admin.ReportsView;
import org.openraffle.ui.events.EventsView;

import java.util.List;

/**
 * Shell for the logged-in views: header with the user and log-out, a side nav with the
 * event list and — while inside an event — that event's pages, and the global footer.
 * Vaadin applies access rules to parent layouts as well, so this one is limited to the
 * same roles as the views inside it.
 */
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final EventService eventService;
    private final SideNav nav = new SideNav();
    private final Div content = new Div();
    private final Span eventLabel = new Span();

    public MainLayout(AuthenticationContext auth, CurrentUser currentUser, EventService eventService, AppVersion version) {
        this.eventService = eventService;

        H1 title = new H1("Open Raffle");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        eventLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Span user = new Span(currentUser.displayName() + (currentUser.isAdmin() ? " (admin)" : ""));
        user.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        Button logout = new Button("Log out", VaadinIcon.SIGN_OUT.create(), e -> auth.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, eventLabel, user, logout);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(eventLabel);
        header.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);
        addToNavbar(header);
        addToDrawer(nav);

        // AppLayout has no footer slot: wrap the routed view and the footer in a column that
        // takes the full content height, so a view's setSizeFull() still gets a real height
        // (a min-height here collapses full-size grids to a single row).
        content.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN);
        content.setHeightFull();
        content.getStyle().set("overflow", "auto");
        setContent(content);
        buildNav(null);
        this.footer = new AppFooter(version);
    }

    private final AppFooter footer;

    @Override
    public void showRouterLayoutContent(HasElement newContent) {
        content.removeAll();
        if (newContent != null) {
            // Fill the space above the footer; min-height 0 lets a full-size view shrink to
            // fit rather than push the footer out of view, and overflow auto makes a view
            // taller than the window scroll above the footer instead of spilling past it.
            newContent.getElement().getStyle().set("flex", "1 1 auto").set("min-height", "0").set("overflow", "auto");
            content.getElement().appendChild(newContent.getElement());
        }
        content.add(footer);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Inside /events/{id}/... show that event's pages in the nav and its name in the header.
        List<String> segments = event.getLocation().getSegments();
        Event current = null;
        if (segments.size() >= 2 && "events".equals(segments.get(0))) {
            try {
                current = eventService.findById(Long.valueOf(segments.get(1))).orElse(null);
            } catch (NumberFormatException ignored) {
                // not an event id
            }
        }
        buildNav(current);
        eventLabel.setText(current == null ? "" : current.getName());
    }

    private void buildNav(Event current) {
        nav.removeAll();
        nav.addItem(new SideNavItem("Events", EventsView.class, VaadinIcon.CALENDAR.create()));
        if (current != null) {
            RouteParameters params = new RouteParameters("eventId", String.valueOf(current.getId()));
            SideNavItem section = new SideNavItem(current.getName());
            section.setPrefixComponent(VaadinIcon.TICKET.create());
            // Set up prizes first, then sell tickets, then draw.
            section.addItem(new SideNavItem("Prizes", PrizesView.class, params, VaadinIcon.GIFT.create()));
            section.addItem(new SideNavItem("Participants", ParticipantsView.class, params, VaadinIcon.USERS.create()));
            section.addItem(new SideNavItem("Reports", ReportsView.class, params, VaadinIcon.CLIPBOARD_TEXT.create()));
            section.addItem(new SideNavItem("Draw", DrawView.class, params, VaadinIcon.TROPHY.create()));
            section.setExpanded(true);
            nav.addItem(section);
        }
    }
}
