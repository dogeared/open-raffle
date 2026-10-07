package org.openraffle.ui.participant;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.service.ParticipantService;
import org.openraffle.service.PrizeService;
import org.openraffle.ui.AppFooter;
import org.openraffle.ui.Paginator;
import org.openraffle.ui.PrizeThumbnail;
import org.openraffle.ui.TicketRangeList;
import org.openraffle.ui.AppVersion;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The page a participant lands on after scanning their QR code. No login required:
 * the unguessable token in the URL identifies them.
 */
@Route("p/:token")
@PageTitle("Your raffle wishlist")
@AnonymousAllowed
public class WishlistView extends VerticalLayout implements BeforeEnterObserver {

    public static final int AUTOSAVE_INTERVAL_MS = 10_000;

    private final ParticipantService participantService;
    private final PrizeService prizeService;
    private final AppVersion version;

    private Participant participant;
    private List<Prize> allPrizes = List.of();
    private final List<Prize> picks = new ArrayList<>();

    private final Div availableList = new Div();
    private final Paginator<Prize> availablePages = new Paginator<>(this::renderAvailablePage);
    private final Div picksList = new Div();
    private final Span savedLabel = new Span();

    /** Picks changed since the last save; flushed by the Save button or the next auto-save. */
    private boolean dirty;
    private Registration pollRegistration;

    public WishlistView(ParticipantService participantService, PrizeService prizeService, AppVersion version) {
        this.participantService = participantService;
        this.prizeService = prizeService;
        this.version = version;
        setMaxWidth("640px");
        addClassNames(LumoUtility.Margin.Horizontal.AUTO, LumoUtility.Padding.MEDIUM);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = event.getRouteParameters().get("token").orElse("");
        participant = participantService.findByToken(token).orElse(null);
        removeAll();
        if (participant == null) {
            add(new H1("Hmm, that link isn't valid"),
                    new Paragraph("Please ask the raffle organizer for a new QR code."),
                    new AppFooter(version));
            return;
        }
        if (participant.getEvent() == null || participant.getEvent().isDeleted()) {
            add(new H1("This raffle is over"),
                    new Paragraph("Thanks for taking part! This link no longer accepts wishlists."),
                    new AppFooter(version));
            return;
        }
        allPrizes = prizeService.findAll(participant.getEvent());
        picks.clear();
        picks.addAll(participant.getWishlist());
        dirty = false;
        build();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Auto-save: the browser polls every few seconds and unsaved changes are written then.
        UI ui = attachEvent.getUI();
        ui.setPollInterval(AUTOSAVE_INTERVAL_MS);
        pollRegistration = ui.addPollListener(e -> {
            if (dirty) {
                save(true);
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        detachEvent.getUI().setPollInterval(-1);
        super.onDetach(detachEvent);
    }

    private void build() {
        H1 title = new H1("Hi " + participant.getName() + "!");
        Span eventName = new Span(participant.getEvent().getName());
        eventName.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.TERTIARY,
                LumoUtility.TextTransform.UPPERCASE, LumoUtility.FontWeight.SEMIBOLD);
        // Tickets are listed one range per line: prefixed ranges in a sentence wrap into a mess.
        Paragraph youHold = new Paragraph(participant.getTicketCount() == 1
                ? "You hold this ticket:" : "You hold these tickets:");
        youHold.addClassNames(LumoUtility.Margin.Bottom.NONE);
        Div intro = new Div(youHold, new TicketRangeList(participant),
                new Paragraph("Pick the prizes you'd like if one of your tickets is drawn, most wanted first."));
        intro.addClassNames(LumoUtility.TextColor.SECONDARY);

        picksList.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Gap.XSMALL);
        availableList.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Gap.XSMALL);

        Button save = new Button("Save my wishlist", VaadinIcon.CHECK.create(), e -> save(false));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        save.setWidthFull();

        savedLabel.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);
        showSavedAt();

        add(eventName, title, intro,
                new H3("Your picks"), picksList,
                new H3("Available prizes"), availableList, availablePages,
                save, savedLabel, new AppFooter(version));
        render();
    }

    private void render() {
        picksList.removeAll();
        if (picks.isEmpty()) {
            Span empty = new Span("Nothing picked yet. Add prizes from the list below.");
            empty.addClassNames(LumoUtility.TextColor.TERTIARY);
            picksList.add(empty);
        }
        for (int i = 0; i < picks.size(); i++) {
            picksList.add(pickRow(i));
        }

        List<Prize> remaining = allPrizes.stream().filter(p -> !picks.contains(p)).toList();
        availablePages.setItems(remaining);
        availablePages.setVisible(!remaining.isEmpty());
    }

    private void renderAvailablePage(List<Prize> pageOfPrizes) {
        availableList.removeAll();
        if (pageOfPrizes.isEmpty()) {
            Span empty = new Span(allPrizes.isEmpty() ? "No prizes have been announced yet." : "You've picked them all!");
            empty.addClassNames(LumoUtility.TextColor.TERTIARY);
            availableList.add(empty);
        }
        for (Prize prize : pageOfPrizes) {
            availableList.add(availableRow(prize));
        }
    }

    private HorizontalLayout pickRow(int index) {
        Prize prize = picks.get(index);
        Span rank = new Span(String.valueOf(index + 1));
        rank.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.PRIMARY);
        rank.setWidth("1.5em");

        Button up = iconButton(VaadinIcon.ARROW_UP, "Move up", () -> swap(index, index - 1));
        up.setEnabled(index > 0);
        Button down = iconButton(VaadinIcon.ARROW_DOWN, "Move down", () -> swap(index, index + 1));
        down.setEnabled(index < picks.size() - 1);
        Button remove = iconButton(VaadinIcon.CLOSE_SMALL, "Remove", () -> {
            picks.remove(index);
            changed();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout row = withThumbnail(prize, row(rank, prizeLabel(prize), up, down, remove), 1);
        row.addClassNames(LumoUtility.Background.PRIMARY_10);
        return row;
    }

    private HorizontalLayout availableRow(Prize prize) {
        Button add = new Button("Add", VaadinIcon.PLUS.create(), e -> {
            picks.add(prize);
            changed();
        });
        add.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        return withThumbnail(prize, row(prizeLabel(prize), add), 0);
    }

    /** Puts the prize's picture, when it has one, just before the label at {@code labelIndex}. */
    private static HorizontalLayout withThumbnail(Prize prize, HorizontalLayout row, int labelIndex) {
        Image thumbnail = PrizeThumbnail.of(prize, "3rem");
        if (thumbnail != null) {
            row.addComponentAtIndex(labelIndex, thumbnail);
        }
        return row;
    }

    private static Div prizeLabel(Prize prize) {
        Div label = new Div(new Span(prize.getName()));
        if (prize.getDescription() != null && !prize.getDescription().isBlank()) {
            Div desc = new Div(new Span(prize.getDescription()));
            desc.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
            label.add(desc);
        }
        return label;
    }

    private static HorizontalLayout row(com.vaadin.flow.component.Component... components) {
        HorizontalLayout row = new HorizontalLayout(components);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Background.CONTRAST_5);
        // The prize label is the first Div; let it take the spare space.
        row.getChildren().filter(Div.class::isInstance).findFirst().ifPresent(row::expand);
        return row;
    }

    private static Button iconButton(VaadinIcon icon, String tooltip, Runnable action) {
        Button button = new Button(icon.create(), e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.setTooltipText(tooltip);
        button.setAriaLabel(tooltip);
        return button;
    }

    private void swap(int a, int b) {
        Prize tmp = picks.get(a);
        picks.set(a, picks.get(b));
        picks.set(b, tmp);
        changed();
    }

    private void changed() {
        dirty = true;
        savedLabel.setText("Unsaved changes — saved automatically in a moment");
        render();
    }

    private void save(boolean automatic) {
        participant = participantService.updateWishlist(participant.getToken(), List.copyOf(picks));
        dirty = false;
        showSavedAt();
        if (!automatic) {
            Notification n = Notification.show("Saved! Good luck 🍀", 4000, Notification.Position.BOTTOM_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }

    private void showSavedAt() {
        Instant at = participant.getWishlistUpdatedAt();
        savedLabel.setText(at == null ? "Not saved yet"
                : "Saved " + DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(at));
    }
}
