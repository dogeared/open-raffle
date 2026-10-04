package org.openraffle.ui.events;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.domain.Event;
import org.openraffle.domain.Organizer;
import org.openraffle.security.CurrentUser;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.service.OrganizerDirectory;
import org.openraffle.ui.MainLayout;
import org.openraffle.ui.ResponsiveColumns;
import org.openraffle.ui.admin.ParticipantsView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The page after login. Organizers pick one of the events they run; admins see every event
 * (deleted ones too) and create, edit, delete and reinstate them.
 */
@Route(value = "events", layout = MainLayout.class)
@PageTitle("Events | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class EventsView extends VerticalLayout implements BeforeEnterObserver {

    private final EventService eventService;
    private final OrganizerDirectory organizerDirectory;
    private final CurrentUser currentUser;
    private final Grid<Event> grid = new Grid<>(Event.class, false);
    private final Div picker = new Div();

    public EventsView(EventService eventService, OrganizerDirectory organizerDirectory, CurrentUser currentUser) {
        this.eventService = eventService;
        this.organizerDirectory = organizerDirectory;
        this.currentUser = currentUser;
        setSizeFull();

        H2 heading = new H2(currentUser.isAdmin() ? "Events" : "Your events");
        HorizontalLayout toolbar = new HorizontalLayout(heading);
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.expand(heading);
        toolbar.setWidthFull();
        if (currentUser.isAdmin()) {
            Button add = new Button("New event", VaadinIcon.PLUS.create(), e -> openEditor(new Event()));
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            toolbar.add(add);
        }
        add(toolbar);

        if (currentUser.isAdmin()) {
            buildAdminGrid();
            add(grid);
        } else {
            picker.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.Gap.SMALL);
            picker.setMaxWidth("640px");
            add(picker);
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // An organizer with exactly one event has nothing to choose: go straight in.
        if (!currentUser.isAdmin()) {
            List<Event> mine = eventService.findAccessible();
            if (mine.size() == 1) {
                event.forwardTo(ParticipantsView.class, params(mine.get(0)));
                return;
            }
        }
        refresh();
    }

    private void refresh() {
        if (currentUser.isAdmin()) {
            grid.setItems(eventService.findAllForAdmin());
        } else {
            picker.removeAll();
            List<Event> mine = eventService.findAccessible();
            if (mine.isEmpty()) {
                picker.add(new Paragraph("You are not listed as an organizer of any event yet. "
                        + "Ask an admin to add " + currentUser.email().orElse("your email") + " to one."));
            }
            for (Event e : mine) {
                Button open = new Button(e.getName(), VaadinIcon.ARROW_RIGHT.create(), click -> open(e));
                open.setIconAfterText(true);
                open.addThemeVariants(ButtonVariant.LUMO_LARGE);
                open.setWidthFull();
                picker.add(open);
            }
        }
    }

    private void buildAdminGrid() {
        grid.addColumn(Event::getName).setHeader("Name").setKey("name").setWidth("6em").setFlexGrow(2).setSortable(true);
        grid.addComponentColumn(e -> {
            Span badge = new Span(e.isDeleted() ? "Deleted" : "Active");
            badge.getElement().getThemeList().add(e.isDeleted() ? "badge error" : "badge success");
            return badge;
        }).setHeader("Status").setKey("status").setWidth("5.5em").setFlexGrow(0);
        // Names for organizers who have logged in; plain emails for the rest.
        Grid.Column<Event> organizers = grid.addColumn(e -> {
            if (e.getOrganizerEmails().isEmpty()) {
                return "—";
            }
            Map<String, Organizer> known = organizerDirectory.byEmail();
            return e.getOrganizerEmails().stream()
                    .map(email -> known.containsKey(email) ? known.get(email).getName() : email)
                    .collect(Collectors.joining(", "));
        }).setHeader("Organizers").setKey("organizers").setWidth("8em").setFlexGrow(3);
        grid.addComponentColumn(e -> {
            Button open = new Button("Open", VaadinIcon.ARROW_RIGHT.create(), click -> open(e));
            open.setIconAfterText(true);
            open.setEnabled(!e.isDeleted());
            Button edit = new Button(VaadinIcon.EDIT.create(), click -> openEditor(e));
            edit.setTooltipText("Edit name and organizers");
            Button toggle = e.isDeleted()
                    ? new Button("Reinstate", VaadinIcon.REFRESH.create(), click -> reinstate(e))
                    : new Button(VaadinIcon.TRASH.create(), click -> confirmDelete(e));
            if (!e.isDeleted()) {
                toggle.setTooltipText("Delete (can be reinstated)");
                toggle.addThemeVariants(ButtonVariant.LUMO_ERROR);
            }
            HorizontalLayout actions = new HorizontalLayout(open, edit, toggle);
            actions.setSpacing(false);
            actions.getChildren().forEach(c -> ((Button) c)
                    .addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL));
            return actions;
        }).setHeader("").setKey("actions").setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        ResponsiveColumns.hideOnNarrowScreens(grid, List.of(organizers));
        grid.setSizeFull();
    }

    private void open(Event e) {
        getUI().ifPresent(ui -> ui.navigate(ParticipantsView.class, params(e)));
    }

    private static RouteParameters params(Event e) {
        return new RouteParameters("eventId", String.valueOf(e.getId()));
    }

    private void openEditor(Event event) {
        boolean isNew = event.getId() == null;
        Dialog dialog = new Dialog(isNew ? "New event" : "Edit event");

        TextField name = new TextField("Name");
        name.setValue(event.getName() == null ? "" : event.getName());
        name.setRequired(true);
        name.setWidthFull();
        // Pick from everyone who has logged in as an organizer; type an email for someone
        // who has not yet (they appear in the list after their first login).
        MultiSelectComboBox<String> organizers = new MultiSelectComboBox<>("Organizers");
        Map<String, Organizer> known = organizerDirectory.byEmail();
        Set<String> choices = new LinkedHashSet<>(organizerDirectory.findAll().stream().map(Organizer::getEmail).toList());
        choices.addAll(event.getOrganizerEmails());
        organizers.setItems(new ArrayList<>(choices));
        organizers.setItemLabelGenerator(email -> known.containsKey(email) ? known.get(email).getLabel() : email);
        organizers.setValue(new LinkedHashSet<>(event.getOrganizerEmails()));
        organizers.setPlaceholder("Choose organizers or type an email");
        organizers.setHelperText("Organizers appear here after they have logged in once. "
                + "To add someone before that, type their login email and press Enter.");
        organizers.setAllowCustomValue(true);
        organizers.addCustomValueSetListener(e -> {
            String email = e.getDetail().trim().toLowerCase();
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                organizers.setInvalid(true);
                organizers.setErrorMessage("\"" + e.getDetail().trim() + "\" is not an email address");
                return;
            }
            organizers.setInvalid(false);
            // setItems() clears the selection, so capture it first and restore it with the new email.
            Set<String> selected = new LinkedHashSet<>(organizers.getValue());
            selected.add(email);
            choices.add(email);
            organizers.setItems(new ArrayList<>(choices));
            organizers.setValue(selected);
        });
        organizers.setWidthFull();

        FormLayout form = new FormLayout(name, organizers);
        form.setColspan(name, 2);
        form.setColspan(organizers, 2);
        dialog.add(form);
        dialog.setWidth("480px");

        Button save = new Button("Save", e -> {
            try {
                event.setName(name.getValue());
                Event saved = eventService.save(event);
                eventService.setOrganizers(saved, organizers.getValue());
                dialog.close();
                refresh();
                Notification.show(isNew ? "Event created" : "Event saved");
            } catch (IllegalArgumentException ex) {
                name.setInvalid(true);
                name.setErrorMessage(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
        name.focus();
    }

    private void confirmDelete(Event event) {
        ConfirmDialog confirm = new ConfirmDialog("Delete event?",
                "\"" + event.getName() + "\" disappears for organizers and its participants' QR links stop working. "
                        + "Nothing is erased; you can reinstate it here later.",
                "Delete", e -> {
                    eventService.softDelete(event);
                    refresh();
                    Notification.show("Event deleted");
                }, "Cancel", e -> {
                });
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }

    private void reinstate(Event event) {
        eventService.reinstate(event);
        refresh();
        Notification.show("Event reinstated").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
