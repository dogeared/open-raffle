package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.ui.admin.ParticipantsView;
import org.openraffle.ui.events.EventsView;

import java.util.List;

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
import static com.github.mvysny.kaributesting.v10.NotificationsKt.expectNotifications;
import static org.assertj.core.api.Assertions.assertThat;

class ParticipantsViewTest extends KaribuTest {

    @SuppressWarnings("unchecked")
    private static Grid<Participant> grid() {
        return _get(Grid.class);
    }

    /** The tickets cell of a grid row, as shown on screen. */
    private static String tickets(int row) {
        return TicketRangeLabelTest.shown((TicketRangeLabel) _getCellComponent(grid(), row, "tickets"));
    }

    private Event openEventAsOrganizer() {
        Event fair = event("Spring fair", "pat@example.com");
        loginAsOrganizer("pat@example.com");
        start();
        navigate("events/" + fair.getId() + "/participants");
        _assertOne(ParticipantsView.class);
        return fair;
    }

    @Test
    void addingAParticipantRequiresAPhoneAndThenShowsTheQrCode() {
        openEventAsOrganizer();

        _click(_get(Button.class, spec -> spec.withText("Add participant")));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Ann");
        _setValue(_get(TextField.class, spec -> spec.withLabel("First ticket #")), "100");
        _setValue(_get(TextField.class, spec -> spec.withLabel("Last ticket #")), "104");
        _click(_get(Button.class, spec -> spec.withText("Create & show QR")));

        TextField phone = _get(TextField.class, spec -> spec.withLabel("Phone"));
        assertThat(phone.isInvalid()).isTrue();
        assertThat(participants.count()).isZero();

        _setValue(phone, "+44 20 7946 0958");
        _click(_get(Button.class, spec -> spec.withText("Create & show QR")));

        // The editor closes and the QR dialog for the new participant opens.
        Dialog qr = _get(Dialog.class, spec -> spec.withPredicate(d -> "Ann".equals(d.getHeaderTitle())));
        assertThat(qr.isOpened()).isTrue();
        assertThat(TicketRangeLabelTest.shown(_get(qr, TicketRangeList.class))).isEqualTo("100 – 104");
        _click(_get(Button.class, spec -> spec.withText("Close")));
        _assertNoDialogs();

        assertThat(_size(grid())).isEqualTo(1);
        assertThat(tickets(0)).isEqualTo("100 – 104");
        assertThat(_getFormattedRow(grid(), 0)).contains("5").doesNotContain("+44 20 7946 0958");
        Participant ann = participants.findAll().get(0);
        assertThat(ann.getPhone()).isEqualTo("+44 20 7946 0958");
        assertThat(ann.getToken()).isNotBlank();
    }

    @Test
    void returningBuyersGetAnotherRangeFromTheEditor() {
        Event fair = openEventAsOrganizer();
        participant(fair, "Ann", 1, 10);
        participant(fair, "Bob", 11, 20);
        navigate("events/" + fair.getId());
        navigate("events/" + fair.getId() + "/participants");

        _click((Button) _getCellComponent(grid(), 0, "name"));
        assertThat(_find(TextField.class, spec -> spec.withLabel("First ticket #"))).hasSize(1);
        _click(_get(Button.class, spec -> spec.withText("Add another range")));
        List<TextField> firsts = _find(TextField.class, spec -> spec.withLabel("First ticket #"));
        List<TextField> lasts = _find(TextField.class, spec -> spec.withLabel("Last ticket #"));
        assertThat(firsts).hasSize(2);
        _setValue(firsts.get(1), "15");
        _setValue(lasts.get(1), "18");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        // 15 – 18 belongs to Bob: refused, dialog stays open.
        assertThat(_find(Dialog.class)).isNotEmpty();
        assertThat(participants.findByToken("token-ann").orElseThrow().getRanges()).hasSize(1);

        _setValue(firsts.get(1), "30");
        _setValue(lasts.get(1), "35");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        _assertNoDialogs();
        assertThat(tickets(0)).isEqualTo("1 – 10, 30 – 35");
        assertThat(_getFormattedRow(grid(), 0)).contains("16");
        assertThat(participants.findByToken("token-ann").orElseThrow().holdsTicket(33)).isTrue();
    }

    @Test
    void prefixedTicketRangesAreEnteredAsPrintedAndValidated() {
        openEventAsOrganizer();

        _click(_get(Button.class, spec -> spec.withText("Add participant")));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Nigel");
        _setValue(_get(TextField.class, spec -> spec.withLabel("Phone")), "555-0102");
        TextField first = _get(TextField.class, spec -> spec.withLabel("First ticket #"));
        TextField last = _get(TextField.class, spec -> spec.withLabel("Last ticket #"));
        _setValue(first, "987-001");
        _setValue(last, "988-100");
        _click(_get(Button.class, spec -> spec.withText("Create & show QR")));

        assertThat(last.isInvalid()).isTrue();
        assertThat(_get(com.vaadin.flow.component.html.Span.class,
                spec -> spec.withPredicate(sp -> sp.getText().contains("share the prefix"))).isVisible()).isTrue();
        assertThat(participants.count()).isZero();

        _setValue(last, "987-100");
        _click(_get(Button.class, spec -> spec.withText("Create & show QR")));
        _click(_get(Button.class, spec -> spec.withText("Close")));

        assertThat(tickets(0)).isEqualTo("987-001 – 987-100");
        assertThat(_getFormattedRow(grid(), 0)).contains("100");
        assertThat(participants.findAll().get(0).holdsTicket("987-042")).isTrue();
        assertThat(participants.findAll().get(0).holdsTicket("988-042")).isFalse();
    }

    @Test
    void theOnlyRangeCannotBeRemovedButExtraOnesCan() {
        Event fair = openEventAsOrganizer();
        Participant ann = participant(fair, "Ann", 1, 10);
        ann.addRange(30, 35);
        participants.save(ann);
        navigate("events/" + fair.getId());
        navigate("events/" + fair.getId() + "/participants");

        _click((Button) _getCellComponent(grid(), 0, "name"));
        List<Button> removes = _find(Button.class, spec -> spec.withPredicate(b -> "Remove range".equals(b.getAriaLabel().orElse(""))));
        assertThat(removes).hasSize(2).allMatch(Button::isEnabled);

        _click(removes.get(1));

        List<Button> left = _find(Button.class, spec -> spec.withPredicate(b -> "Remove range".equals(b.getAriaLabel().orElse(""))));
        assertThat(left).hasSize(1);
        assertThat(left.get(0).isEnabled()).isFalse();
        _click(_get(Button.class, spec -> spec.withText("Save")));
        assertThat(participants.findByToken("token-ann").orElseThrow().getTicketRangeLabel()).isEqualTo("1 – 10");
    }

    @Test
    void overlappingTicketsAreReportedNotSaved() {
        Event fair = openEventAsOrganizer();
        participant(fair, "Ann", 1, 10);
        navigate("events/" + fair.getId());
        navigate("events/" + fair.getId() + "/participants");

        _click(_get(Button.class, spec -> spec.withText("Add participant")));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Bob");
        _setValue(_get(TextField.class, spec -> spec.withLabel("Phone")), "555-0101");
        _setValue(_get(TextField.class, spec -> spec.withLabel("First ticket #")), "5");
        _setValue(_get(TextField.class, spec -> spec.withLabel("Last ticket #")), "15");
        _click(_get(Button.class, spec -> spec.withText("Create & show QR")));

        assertThat(participants.count()).isEqualTo(1);
        assertThat(_find(Dialog.class)).isNotEmpty(); // editor stays open
        assertThat(com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications())
                .anyMatch(n -> n.getElement().getTextRecursively().contains("Ann (1 – 10)")
                        || n.getElement().getProperty("text", "").contains("Ann (1 – 10)"));
    }

    @Test
    void nameOpensTheEditorAndWishlistOpensTheRankedList() {
        Event fair = openEventAsOrganizer();
        Prize bike = prize(fair, "Bike");
        Prize book = prize(fair, "Book");
        Participant ann = participant(fair, "Ann", 1, 10);
        ann.setWishlist(List.of(book, bike));
        participants.save(ann);
        navigate("events/" + fair.getId());
        navigate("events/" + fair.getId() + "/participants");

        _click((Button) _getCellComponent(grid(), 0, "name"));
        Dialog editor = _get(Dialog.class);
        assertThat(editor.getHeaderTitle()).isEqualTo("Edit participant");
        assertThat(_get(TextField.class, spec -> spec.withLabel("Name")).getValue()).isEqualTo("Ann");
        _click(_get(Button.class, spec -> spec.withText("Cancel")));
        _assertNoDialogs();

        Button wishlist = (Button) _getCellComponent(grid(), 0, "wishlist");
        assertThat(wishlist.getText()).isEqualTo("Book › Bike");
        _click(wishlist);
        Dialog picks = _get(Dialog.class);
        assertThat(picks.getHeaderTitle()).isEqualTo("Ann's picks");
        assertThat(_find(ListItem.class)).extracting(li -> li.getElement().getTextRecursively())
                .containsExactly("Book", "Bike");

        // Organizers can take a prize off the list from here.
        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Remove Book from the list".equals(b.getAriaLabel().orElse("")))));

        assertThat(participants.findByToken("token-ann").orElseThrow().getWishlist()).extracting(Prize::getName).containsExactly("Bike");
        assertThat(_get(Dialog.class).getHeaderTitle()).isEqualTo("Ann's picks"); // reopened with the rest
        assertThat(_find(ListItem.class)).extracting(li -> li.getElement().getTextRecursively()).containsExactly("Bike");
        assertThat(((Button) _getCellComponent(grid(), 0, "wishlist")).getText()).isEqualTo("Bike");
    }

    @Test
    void deletingAParticipantAsksFirst() {
        Event fair = openEventAsOrganizer();
        participant(fair, "Ann", 1, 10);
        navigate("events/" + fair.getId());
        navigate("events/" + fair.getId() + "/participants");

        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 0, "actions");
        _click((Button) actions.getComponentAt(2));
        confirm(_get(ConfirmDialog.class));

        assertThat(participants.count()).isZero();
        assertThat(_size(grid())).isZero();
        expectNotifications("Participant deleted");
    }

    @Test
    void participantsAreAlphabeticalAndPaginated() {
        Event fair = openEventAsOrganizer();
        for (int i = 1; i <= 12; i++) {
            // Names in reverse order of their tickets, to prove the sort is by name.
            participant(fair, "Person " + (char) ('Z' - i), i * 10, i * 10 + 5);
        }
        navigate("events/" + fair.getId());
        navigate("events/" + fair.getId() + "/participants");

        assertThat(_size(grid())).isEqualTo(10);
        assertThat(((Button) _getCellComponent(grid(), 0, "name")).getText()).isEqualTo("Person N");
        assertThat(((Button) _getCellComponent(grid(), 9, "name")).getText()).isEqualTo("Person W");
        assertThat(_get(com.vaadin.flow.component.html.Span.class, spec -> spec.withText("1–10 of 12"))).isNotNull();

        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));

        assertThat(_size(grid())).isEqualTo(2);
        assertThat(((Button) _getCellComponent(grid(), 0, "name")).getText()).isEqualTo("Person X");
        assertThat(((Button) _getCellComponent(grid(), 1, "name")).getText()).isEqualTo("Person Y");
    }

    @Test
    void organizersNotListedOnTheEventAreSentBackToTheList() {
        Event fair = event("Spring fair", "other@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events/" + fair.getId() + "/participants");

        _assertNone(ParticipantsView.class);
        _assertOne(EventsView.class);
        expectNotifications("That event isn't available to you.");
    }

    @Test
    void adminsCanOpenAnyEvent() {
        Event fair = event("Spring fair", "other@example.com");
        loginAsAdmin();
        start();

        navigate("events/" + fair.getId() + "/participants");

        _assertOne(ParticipantsView.class);
    }
}
