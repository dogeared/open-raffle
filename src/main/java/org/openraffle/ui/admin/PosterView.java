package org.openraffle.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.Event;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.service.QrCodeService;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * A poster for the public prize list, laid out for a Letter sheet: the event name on top,
 * the QR code in the middle, "Scan for available prizes!" underneath, each as large as the
 * page allows and centered both ways. No app chrome, so what you see is what prints.
 */
@Route("events/:eventId/poster")
@PageTitle("Prize poster | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class PosterView extends Div implements BeforeEnterObserver {

    public static final String MESSAGE = "Scan for available prizes!";

    private final EventService eventService;
    private final QrCodeService qrCodeService;

    public PosterView(EventService eventService, QrCodeService qrCodeService) {
        this.eventService = eventService;
        this.qrCodeService = qrCodeService;
        addClassName("poster");
    }

    public static RouteParameters paramsFor(Event event) {
        return new RouteParameters("eventId", String.valueOf(event.getId()));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent enter) {
        EventScopedView.resolve(enter, eventService).ifPresent(this::build);
    }

    private void build(Event event) {
        removeAll();

        H1 name = new H1(event.getName());
        name.addClassName("poster-title");
        // One line if at all possible: the size shrinks with the name's length (a glyph is
        // roughly 0.58em wide in this font), capped so short names do not become absurd.
        String glyphs = String.format(Locale.ROOT, "%.2f", Math.max(1, event.getName().length()) * 0.58);
        name.getStyle().set("font-size", "min(13vmin, calc(92vw / " + glyphs + "))");

        StreamResource png = new StreamResource("prizes-" + event.getSlug() + ".png",
                () -> new ByteArrayInputStream(qrCodeService.pngFor(event, 1024)));
        Image qr = new Image(png, "QR code for the public prize list of " + event.getName());
        qr.addClassName("poster-qr");

        Paragraph message = new Paragraph();
        message.addClassName("poster-message");
        // The message breaks only between "for" and "available", never mid-phrase.
        message.getElement().setProperty("innerHTML", "Scan for<br>available prizes!");

        // Screen-only controls; hidden when printing.
        Button print = new Button("Print", VaadinIcon.PRINT.create(), e -> getElement().executeJs("window.print()"));
        print.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor download = new Anchor(png, "Download QR PNG");
        download.getElement().setAttribute("download", true);
        Anchor back = new Anchor("events/" + event.getId(), "Back to prizes");
        HorizontalLayout controls = new HorizontalLayout(back, download, print);
        controls.addClassName("poster-controls");
        controls.setAlignItems(HorizontalLayout.Alignment.CENTER);

        // Page setup travels with this view rather than the global stylesheet, so printing
        // other pages is unaffected.
        Element pageSetup = new Element("style");
        pageSetup.setText("@page { size: letter portrait; margin: 0.5in; }");

        add(controls, name, qr, message);
        getElement().appendChild(pageSetup);
    }
}
