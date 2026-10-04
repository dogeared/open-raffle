package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.ui.admin.PrizesView;
import org.openraffle.ui.events.EventsView;

import java.util.Set;

import static com.github.mvysny.kaributesting.v10.GridKt._get;
import static com.github.mvysny.kaributesting.v10.GridKt._getCellComponent;
import static com.github.mvysny.kaributesting.v10.GridKt._getFormattedRow;
import static com.github.mvysny.kaributesting.v10.GridKt._size;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNoDialogs;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class EventsViewTest extends KaribuTest {

    @SuppressWarnings("unchecked")
    private static Grid<Event> grid() {
        return _get(Grid.class);
    }

    private static Button actionButton(int row, String text) {
        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), row, "actions");
        return actions.getChildren().map(Button.class::cast)
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    @Test
    void adminSeesEveryEventAndCanCreateOne() {
        event("Spring fair", "pat@example.com");
        loginAsAdmin();
        start();

        navigate("events");

        _assertOne(EventsView.class);
        assertThat(_size(grid())).isEqualTo(1);
        assertThat(_getFormattedRow(grid(), 0)).contains("Spring fair", "pat@example.com");

        _click(_get(Button.class, spec -> spec.withText("New event")));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Autumn fair");
        _setValue(organizerPicker(), Set.of("sam@example.com"));
        _click(_get(Button.class, spec -> spec.withText("Save")));

        _assertNoDialogs();
        assertThat(_size(grid())).isEqualTo(2);
        Event autumn = events.findByNameIgnoreCase("Autumn fair").orElseThrow();
        assertThat(autumn.getOrganizerEmails()).containsExactly("sam@example.com");
    }

    @SuppressWarnings("unchecked")
    private static MultiSelectComboBox<String> organizerPicker() {
        return _get(MultiSelectComboBox.class, spec -> spec.withLabel("Organizers"));
    }

    private static void typeCustomEmail(MultiSelectComboBox<String> picker, String typed) {
        ComponentUtil.fireEvent(picker, new MultiSelectComboBox.CustomValueSetEvent<>(picker, true, typed));
    }

    @Test
    void thePickerOffersKnownOrganizersAndAcceptsTypedEmails() {
        knownOrganizer("pat@example.com", "Pat Smith");
        knownOrganizer("sam@example.com", "Sam Jones");
        loginAsAdmin();
        start();
        navigate("events");

        _click(_get(Button.class, spec -> spec.withText("New event")));
        MultiSelectComboBox<String> picker = organizerPicker();
        assertThat(picker.getListDataView().getItems()).containsExactly("pat@example.com", "sam@example.com");
        assertThat(picker.getItemLabelGenerator().apply("pat@example.com")).isEqualTo("Pat Smith <pat@example.com>");

        _setValue(picker, Set.of("pat@example.com"));
        typeCustomEmail(picker, " New.Person@Example.com ");
        assertThat(picker.isInvalid()).isFalse();
        assertThat(picker.getValue()).containsExactlyInAnyOrder("pat@example.com", "new.person@example.com");
        assertThat(picker.getListDataView().getItems()).contains("new.person@example.com");

        typeCustomEmail(picker, "not an email");
        assertThat(picker.isInvalid()).isTrue();
        assertThat(picker.getErrorMessage()).contains("not an email address");
        assertThat(picker.getValue()).hasSize(2);

        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Winter fair");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        Event winter = events.findByNameIgnoreCase("Winter fair").orElseThrow();
        assertThat(winter.getOrganizerEmails()).containsExactlyInAnyOrder("pat@example.com", "new.person@example.com");
        assertThat(_getFormattedRow(grid(), 0)).anySatisfy(cell -> assertThat(cell).contains("Pat Smith").contains("new.person@example.com"));
    }

    @Test
    void editingAnEventPreselectsItsOrganizersEvenIfTheyNeverLoggedIn() {
        event("Spring fair", "ghost@example.com");
        knownOrganizer("pat@example.com", "Pat Smith");
        loginAsAdmin();
        start();
        navigate("events");

        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 0, "actions");
        _click((Button) actions.getComponentAt(1)); // the pencil
        MultiSelectComboBox<String> picker = organizerPicker();

        assertThat(picker.getValue()).containsExactly("ghost@example.com");
        assertThat(picker.getListDataView().getItems()).containsExactlyInAnyOrder("pat@example.com", "ghost@example.com");
    }

    @Test
    void duplicateNamesAreRejectedInTheDialog() {
        event("Spring fair");
        loginAsAdmin();
        start();
        navigate("events");

        _click(_get(Button.class, spec -> spec.withText("New event")));
        TextField name = _get(TextField.class, spec -> spec.withLabel("Name"));
        _setValue(name, "spring FAIR");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(name.isInvalid()).isTrue();
        assertThat(name.getErrorMessage()).contains("already exists");
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void adminCanDeleteAndReinstateAnEvent() {
        event("Spring fair");
        loginAsAdmin();
        start();
        navigate("events");

        // The trash button has no text; it is the last action in the row.
        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 0, "actions");
        Button trash = (Button) actions.getComponentAt(2);
        _click(trash);
        confirm(_get(ConfirmDialog.class));

        assertThat(((Span) _getCellComponent(grid(), 0, "status")).getText()).isEqualTo("Deleted");
        assertThat(events.findAll().get(0).isDeleted()).isTrue();
        assertThat(actionButton(0, "Open").isEnabled()).isFalse();

        _click(actionButton(0, "Reinstate"));

        assertThat(((Span) _getCellComponent(grid(), 0, "status")).getText()).isEqualTo("Active");
        assertThat(events.findAll().get(0).isDeleted()).isFalse();
    }

    @Test
    void organizerWithSeveralEventsPicksOne() {
        Event spring = event("Spring fair", "pat@example.com");
        event("Autumn fair", "pat@example.com");
        event("Not mine", "other@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events");

        _assertNone(Grid.class);
        _assertNone(Button.class, spec -> spec.withText("New event"));
        assertThat(_find(Button.class, spec -> spec.withText("Spring fair"))).hasSize(1);
        assertThat(_find(Button.class, spec -> spec.withText("Autumn fair"))).hasSize(1);
        _assertNone(Button.class, spec -> spec.withText("Not mine"));

        _click(_get(Button.class, spec -> spec.withText("Spring fair")));

        _assertOne(PrizesView.class);
        assertThat(_get(Span.class, spec -> spec.withText(spring.getName()))).isNotNull();
    }

    @Test
    void organizerWithOneEventGoesStraightIn() {
        event("Spring fair", "pat@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events");

        _assertNone(EventsView.class);
        _assertOne(PrizesView.class);
    }

    @Test
    void organizerWithNoEventsIsToldWhomToAsk() {
        event("Spring fair", "other@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events");

        _assertOne(EventsView.class);
        assertThat(_get(Paragraph.class, spec -> spec.withPredicate(p -> p.getText().contains("not listed")))
                .getText()).contains("pat@example.com");
    }

    @Test
    void anonymousVisitorsDoNotReachTheEventList() {
        start();

        navigate("events");

        _assertNone(EventsView.class);
    }
}
