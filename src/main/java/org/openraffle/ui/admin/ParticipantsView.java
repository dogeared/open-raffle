package org.openraffle.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.OrderedList;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.PhoneNumbers;
import org.openraffle.domain.Prize;
import org.openraffle.domain.TicketRange;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.service.ParticipantService;
import org.openraffle.service.ParticipantService.TicketRangeConflictException;
import org.openraffle.service.QrCodeService;
import org.openraffle.ui.MainLayout;
import org.openraffle.ui.Paginator;
import org.openraffle.ui.ResponsiveColumns;
import org.openraffle.ui.TicketRangeLabel;
import org.openraffle.ui.TicketRangeList;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "events/:eventId/participants", layout = MainLayout.class)
@PageTitle("Participants | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class ParticipantsView extends VerticalLayout implements BeforeEnterObserver {

    private final ParticipantService participantService;
    private final QrCodeService qrCodeService;
    private final EventService eventService;
    private final Grid<Participant> grid = new Grid<>(Participant.class, false);
    private final Paginator<Participant> pages = new Paginator<>(grid::setItems);
    private Event event;

    public ParticipantsView(ParticipantService participantService, QrCodeService qrCodeService, EventService eventService) {
        this.participantService = participantService;
        this.qrCodeService = qrCodeService;
        this.eventService = eventService;
        setSizeFull();

        Button add = new Button("Add participant", VaadinIcon.PLUS.create(), e -> openEditor(newParticipant()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        H2 heading = new H2("Participants");
        HorizontalLayout toolbar = new HorizontalLayout(heading, add);
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.expand(heading);
        toolbar.setWidthFull();

        // The name opens the editor, like the pencil button: an extra cue. No phone column:
        // this screen is turned towards participants when they scan their QR code.
        grid.addComponentColumn(p -> {
            Button name = new Button(p.getName(), e -> openEditor(p));
            name.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            return name;
        }).setHeader("Name").setKey("name").setWidth("6em").setFlexGrow(2);
        // Alphabetical, server-side (the grid only holds one page, so column sorting would mislead).
        grid.addComponentColumn(TicketRangeLabel::new).setHeader("Tickets").setKey("tickets").setWidth("7em").setFlexGrow(2);
        Grid.Column<Participant> count = grid.addColumn(Participant::getTicketCount)
                .setHeader("Count").setKey("count").setWidth("6em").setFlexGrow(0);
        // The wishlist summary opens a dialog with the full ranked list.
        grid.addComponentColumn(p -> {
            if (p.getWishlist().isEmpty()) {
                return new Span("—");
            }
            String summary = p.getWishlist().stream().map(Prize::getName).collect(Collectors.joining(" › "));
            Button open = new Button(summary, e -> showWishlist(p));
            open.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            open.setTooltipText(summary);
            // One line with an ellipsis: the dialog has the full list, and organizers are on
            // laptops or tablets where this column has room.
            open.addClassNames("wishlist-summary");
            return open;
        }).setHeader("Wishlist (in order)").setKey("wishlist").setWidth("8em").setFlexGrow(3);
        grid.addComponentColumn(p -> {
            Button qr = new Button(VaadinIcon.QRCODE.create(), e -> showQr(p));
            qr.setTooltipText("Show QR code");
            Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditor(p));
            Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(p));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(qr, edit, delete);
            actions.setSpacing(false);
            actions.getChildren().forEach(c -> ((Button) c)
                    .addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL));
            return actions;
        }).setHeader("").setKey("actions").setAutoWidth(true).setFlexGrow(0);
        // Long names and ticket lists wrap onto more lines instead of being cut off; the
        // wishlist stays one truncated line (the dialog has it all), and on a phone the
        // count column gives way.
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        ResponsiveColumns.hideOnNarrowScreens(grid, List.of(count));
        grid.setSizeFull();

        add(toolbar, grid, pages);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent enter) {
        EventScopedView.resolve(enter, eventService).ifPresent(e -> {
            event = e;
            refresh();
        });
    }

    private Participant newParticipant() {
        Participant p = new Participant();
        p.setEvent(event);
        return p;
    }

    private void refresh() {
        pages.setItems(participantService.findAll(event));
    }

    private void openEditor(Participant participant) {
        boolean isNew = participant.getId() == null;
        Dialog dialog = new Dialog(isNew ? "New participant" : "Edit participant");

        TextField name = new TextField("Name");
        TextField phone = new TextField("Phone");
        phone.setPlaceholder("(555) 123-4567");
        phone.setHelperText("Outside the US, start with + and the country code, e.g. +44 20 7946 0958");
        phone.setMaxLength(32);

        BeanValidationBinder<Participant> binder = new BeanValidationBinder<>(Participant.class);
        binder.forField(name).asRequired("Name is required").bind(Participant::getName, Participant::setName);
        binder.forField(phone).asRequired("Phone is required")
                .withValidator(Participant::isPlausiblePhone, Participant.PHONE_RULE)
                // Show the stored number the way it is displayed elsewhere; what is typed is saved as typed.
                .bind(p -> PhoneNumbers.format(p.getPhone()), Participant::setPhone);
        if (!isNew) {
            binder.readBean(participant);
        }

        // One row per ticket range; people come back to buy more, so ranges can be added.
        VerticalLayout rangeRows = new VerticalLayout();
        rangeRows.setPadding(false);
        rangeRows.setSpacing(false);
        Span rangesLabel = new Span("Tickets");
        rangesLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.MEDIUM, LumoUtility.TextColor.SECONDARY);
        Span rangesHint = new Span("Exactly as printed on the roll: digits, or a dashed prefix and digits, e.g. 1 – 100 or 987-001 – 987-100.");
        rangesHint.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);
        Button addRange = new Button("Add another range", VaadinIcon.PLUS.create(), e -> addRangeRow(rangeRows, null).focus());
        addRange.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        if (participant.getRanges().isEmpty()) {
            addRangeRow(rangeRows, null);
        } else {
            participant.getRangesInOrder().forEach(range -> addRangeRow(rangeRows, range));
        }

        FormLayout form = new FormLayout(name, phone);
        Div ticketsHeader = new Div(rangesLabel, rangesHint);
        ticketsHeader.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Margin.Top.SMALL);
        dialog.add(form, ticketsHeader, rangeRows, addRange);
        dialog.setWidth("520px");

        Button save = new Button(isNew ? "Create & show QR" : "Save", e -> {
            try {
                binder.writeBean(participant);
                List<TicketRange> ranges = readRanges(rangeRows);
                if (ranges == null) {
                    return; // a row is incomplete; its fields are marked
                }
                participant.setRanges(new ArrayList<>(ranges));
                Participant saved = participantService.save(participant);
                dialog.close();
                refresh();
                if (isNew) {
                    showQr(saved);
                }
            } catch (ValidationException ex) {
                // shown inline by binder
            } catch (TicketRangeConflictException ex) {
                Notification n = Notification.show(ex.getMessage(), 6000, Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage(), 6000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
        name.focus();
    }

    /** A "first – last" pair with a remove button; returns the first-ticket field for focusing. */
    private static TextField addRangeRow(VerticalLayout rows, TicketRange existing) {
        TextField first = new TextField("First ticket #");
        TextField last = new TextField("Last ticket #");
        first.setPlaceholder("1 or 987-001");
        last.setPlaceholder("100 or 987-100");
        first.setWidthFull();
        last.setWidthFull();
        // The last ticket defaults to the first one; select it on focus so typing replaces it.
        last.setAutoselect(true);
        if (existing != null) {
            first.setValue(existing.getStartLabel());
            last.setValue(existing.getEndLabel());
        }
        // Prefill in the browser at "change" time (before focus moves on) so autoselect on
        // the last-ticket field highlights the value; the server listener is the fallback.
        first.getElement().executeJs(
                "this.addEventListener('change', () => { const end = $0;"
                        + " if (!end.value && this.value) { end.value = this.value; end.dispatchEvent(new Event('change')); } })",
                last.getElement());
        first.addValueChangeListener(e -> {
            if (e.isFromClient() && last.isEmpty() && e.getValue() != null && !e.getValue().isBlank()) {
                last.setValue(e.getValue());
            }
        });

        // Both fields share the width; the remove button lines up with the inputs. Errors go
        // on a line under the row (field-level error text would make one field taller than
        // the other and stagger the row).
        HorizontalLayout fields = new HorizontalLayout(first, last);
        fields.setWidthFull();
        fields.setAlignItems(Alignment.END);
        fields.setFlexGrow(1, first, last);
        Span error = new Span();
        error.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.ERROR);
        error.setVisible(false);
        VerticalLayout row = new VerticalLayout(fields, error);
        row.setPadding(false);
        row.setSpacing(false);
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> {
            rows.remove(row);
            updateRemoveButtons(rows);
        });
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        remove.setAriaLabel("Remove range");
        remove.setTooltipText("Remove range");
        fields.add(remove);
        rows.add(row);
        updateRemoveButtons(rows);
        return first;
    }

    /** The only remaining range cannot be removed. */
    private static void updateRemoveButtons(VerticalLayout rows) {
        long count = rows.getChildren().count();
        rows.getChildren().forEach(row -> fieldsOf(row).getChildren()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .forEach(b -> b.setEnabled(count > 1)));
    }

    private static HorizontalLayout fieldsOf(com.vaadin.flow.component.Component row) {
        return (HorizontalLayout) ((VerticalLayout) row).getComponentAt(0);
    }

    private static Span errorOf(com.vaadin.flow.component.Component row) {
        return (Span) ((VerticalLayout) row).getComponentAt(1);
    }

    /** The ranges typed into the rows, or null (with the offending fields marked) if a row is incomplete or invalid. */
    private static List<TicketRange> readRanges(VerticalLayout rows) {
        List<TicketRange> ranges = new ArrayList<>();
        boolean complete = true;
        for (var row : rows.getChildren().toList()) {
            HorizontalLayout fields = fieldsOf(row);
            TextField first = (TextField) fields.getComponentAt(0);
            TextField last = (TextField) fields.getComponentAt(1);
            Span error = errorOf(row);
            String problem = null;
            boolean firstMissing = first.getValue() == null || first.getValue().isBlank();
            boolean lastMissing = last.getValue() == null || last.getValue().isBlank();
            first.setInvalid(firstMissing);
            last.setInvalid(lastMissing);
            if (firstMissing || lastMissing) {
                problem = "Both the first and the last ticket are required";
            } else {
                try {
                    ranges.add(TicketRange.of(first.getValue(), last.getValue()));
                } catch (IllegalArgumentException ex) {
                    problem = ex.getMessage();
                    first.setInvalid(true);
                    last.setInvalid(true);
                }
            }
            error.setText(problem == null ? "" : problem);
            error.setVisible(problem != null);
            complete &= problem == null;
        }
        return complete ? ranges : null;
    }

    private void showWishlist(Participant participant) {
        Dialog dialog = new Dialog(participant.getName() + "'s picks");
        OrderedList list = new OrderedList();
        for (Prize prize : participant.getWishlist()) {
            ListItem item = new ListItem();
            Span name = new Span(prize.getName());
            item.add(name);
            if (prize.isClaimed()) {
                Span claimed = new Span(prize.isClaimedBy(participant)
                        ? " — they took this one" : " — claimed by " + prize.getClaimedBy().getName());
                claimed.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.TERTIARY);
                item.add(claimed);
            }
            // Organizers can take a prize off the list, e.g. one the participant no longer wants.
            Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> {
                Participant updated = participantService.removeFromWishlist(participant, prize);
                dialog.close();
                refresh();
                Notification.show("Removed " + prize.getName() + " from " + participant.getName() + "'s picks");
                if (!updated.getWishlist().isEmpty()) {
                    showWishlist(updated);
                }
            });
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Remove " + prize.getName() + " from the list");
            remove.setTooltipText("Remove from their list");
            remove.addClassNames(LumoUtility.Margin.Left.SMALL);
            item.add(remove);
            list.add(item);
        }
        Paragraph hint = new Paragraph("Most wanted first, as ranked by " + participant.getName() + ".");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        if (participant.getWishlistUpdatedAt() != null) {
            hint.setText(hint.getText() + " Last saved " + participant.getWishlistUpdatedAt() + ".");
        }
        dialog.add(list, hint);
        dialog.setMaxWidth("480px");
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    private void showQr(Participant participant) {
        Dialog dialog = new Dialog(participant.getName());
        String url = qrCodeService.wishlistUrl(participant);

        StreamResource png = new StreamResource("qr-" + participant.getId() + ".png",
                () -> new ByteArrayInputStream(qrCodeService.pngFor(participant, 512)));
        Image image = new Image(png, "QR code for " + participant.getName());
        image.setWidth("min(70vw, 360px)");
        image.setHeight("min(70vw, 360px)");

        // Shown to the participant, so it speaks to them.
        Paragraph holds = new Paragraph(participant.getTicketCount() == 1 ? "Your ticket:" : "Your tickets:");
        holds.addClassNames(LumoUtility.Margin.NONE);
        Div tickets = new Div(holds, new TicketRangeList(participant));
        Anchor link = new Anchor(url, url);
        link.setTarget("_blank");
        link.addClassNames(LumoUtility.FontSize.SMALL);
        Paragraph hint = new Paragraph("Scan to choose the prizes you'd like if your ticket is drawn.");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        VerticalLayout content = new VerticalLayout(tickets, hint, image, link);
        content.setAlignItems(Alignment.CENTER);
        content.setPadding(false);
        dialog.add(content);

        Anchor download = new Anchor(png, "Download PNG");
        download.getElement().setAttribute("download", true);
        dialog.getFooter().add(download, new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    private void confirmDelete(Participant participant) {
        ConfirmDialog confirm = new ConfirmDialog("Delete participant?",
                participant.getName() + " (tickets " + participant.getTicketRangeLabel()
                        + ") and their wishlist will be removed. Their QR code will stop working.",
                "Delete", e -> {
                    participantService.delete(participant);
                    refresh();
                    Notification.show("Participant deleted");
                }, "Cancel", e -> {
                });
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }
}
