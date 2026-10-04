package org.openraffle.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.domain.TicketRange;
import org.openraffle.ui.admin.DrawView;

import java.util.List;
import java.util.Optional;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class DrawViewTest extends KaribuTest {

    private Event fair;
    private Prize bike;
    private Prize book;
    private Participant ann;
    private Participant bob;

    private void openDraw() {
        fair = event("Spring fair", "pat@example.com");
        bike = prize(fair, "Bike");
        book = prize(fair, "Book");
        prize(fair, "Mug");
        ann = participant(fair, "Ann", 1, 10);
        ann.setWishlist(List.of(bike, book));
        ann = participants.save(ann);
        bob = participant(fair, "Bob", 11, 20);
        bob.addRange(40, 45);
        bob.setWishlist(List.of(bike));
        bob = participants.save(bob);
        loginAsOrganizer("pat@example.com");
        start();
        navigate("events/" + fair.getId() + "/draw");
        _assertOne(DrawView.class);
    }

    private static boolean isInside(Component component, Component ancestor) {
        Optional<Component> parent = component.getParent();
        while (parent.isPresent()) {
            if (parent.get() == ancestor) {
                return true;
            }
            parent = parent.get().getParent();
        }
        return false;
    }

    private void lookUp(int ticket) {
        lookUp(String.valueOf(ticket));
    }

    private void lookUp(String printed) {
        _setValue(_get(TextField.class, spec -> spec.withLabel("Drawn ticket #")), printed);
        _click(_get(Button.class, spec -> spec.withText("Look up")));
    }

    @Test
    void lookingUpATicketShowsTheWinnerTheirPhoneAndRankedPicks() {
        openDraw();

        lookUp(7);

        assertThat(_get(H3.class).getText()).contains("Ann");
        assertThat(_get(Anchor.class, spec -> spec.withText("555-0100")).getHref()).isEqualTo("tel:5550100");
        Details others = _get(Details.class);
        assertThat(others.getSummaryText()).isEqualTo("Other available prizes (1)");
        assertThat(others.isOpened()).isFalse();
        // Ranked picks first; the one checkbox inside the collapsed panel is the off-list prize.
        assertThat(_find(Checkbox.class)).extracting(Checkbox::getLabel).containsExactly("Bike", "Book", "Mug");
        assertThat(isInside(_get(Checkbox.class, spec -> spec.withLabel("Mug")), others)).isTrue();
        assertThat(isInside(_get(Checkbox.class, spec -> spec.withLabel("Bike")), others)).isFalse();
    }

    @Test
    void ticketsFromAnyOfTheWinnersRangesAreFound() {
        openDraw();

        lookUp(42);

        assertThat(_get(H3.class).getText()).contains("Bob");
        assertThat(_get(com.vaadin.flow.component.html.Paragraph.class,
                spec -> spec.withPredicate(p -> p.getText().startsWith("Holds tickets"))).getText())
                .isEqualTo("Holds tickets 11 – 20, 40 – 45");
    }

    @Test
    void prefixedTicketsAreLookedUpExactlyAsPrinted() {
        openDraw();
        Participant nigel = participant(fair, "Nigel", 900, 901);
        nigel.setRanges(new java.util.ArrayList<>(List.of(TicketRange.of("987-001", "987-100"))));
        participants.save(nigel);
        navigate("events/" + fair.getId() + "/participants");
        navigate("events/" + fair.getId() + "/draw");

        lookUp("987-042");
        assertThat(_get(H3.class).getText()).contains("Nigel");
        assertThat(_get(com.vaadin.flow.component.html.Paragraph.class,
                spec -> spec.withPredicate(p -> p.getText().startsWith("Holds tickets"))).getText())
                .isEqualTo("Holds tickets 987-001 – 987-100");

        lookUp("988-042");
        assertThat(_get(Span.class, spec -> spec.withPredicate(s -> s.getText().startsWith("No participant"))).getText())
                .isEqualTo("No participant holds ticket 988-042.");

        lookUp("nope");
        assertThat(_get(Span.class, spec -> spec.withPredicate(s -> s.getText().contains("not a ticket number")))).isNotNull();
    }

    @Test
    void unknownTicketsSaySo() {
        openDraw();

        lookUp(999);

        assertThat(_get(Span.class, spec -> spec.withPredicate(s -> s.getText().contains("No participant"))).getText())
                .isEqualTo("No participant holds ticket 999.");
    }

    @Test
    void tickingAPrizeClaimsItAndLaterWinnersSeeItCrossedOut() {
        openDraw();
        lookUp(7);

        _setValue(_get(Checkbox.class, spec -> spec.withLabel("Bike")), true);

        Prize claimed = prizes.findById(bike.getId()).orElseThrow();
        assertThat(claimed.isClaimedBy(ann)).isTrue();
        assertThat(_get(Checkbox.class, spec -> spec.withLabel("Bike")).getValue()).isTrue();

        lookUp(15);

        assertThat(_get(H3.class).getText()).contains("Bob");
        _assertNone(Checkbox.class, spec -> spec.withLabel("Bike"));
        Span crossed = _get(Span.class, spec -> spec.withText("Bike"));
        assertThat(crossed.getStyle().get("text-decoration")).isEqualTo("line-through");
        assertThat(_get(Span.class, spec -> spec.withText("claimed by Ann"))).isNotNull();
        assertThat(_get(Span.class, spec -> spec.withPredicate(s -> s.getText().startsWith("Everything on their list")))).isNotNull();
        assertThat(_get(Details.class).isOpened()).isTrue();
    }

    @Test
    void organizersCanTakeAPrizeOffTheWinnersListFromTheDrawPage() {
        openDraw();
        lookUp(7);
        _setValue(_get(Checkbox.class, spec -> spec.withLabel("Bike")), true);

        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Remove Bike from the list".equals(b.getAriaLabel().orElse("")))));

        Participant reloaded = participants.findById(ann.getId()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Book");
        assertThat(prizes.findById(bike.getId()).orElseThrow().isClaimed()).isFalse();
        assertThat(_find(Checkbox.class)).extracting(Checkbox::getLabel).startsWith("Book");
        // Bike is now an "other available prize" again.
        assertThat(_get(Details.class).getSummaryText()).isEqualTo("Other available prizes (2)");
    }

    @Test
    void untickingReleasesTheClaim() {
        openDraw();
        lookUp(7);
        _setValue(_get(Checkbox.class, spec -> spec.withLabel("Bike")), true);

        _setValue(_get(Checkbox.class, spec -> spec.withLabel("Bike")), false);

        assertThat(prizes.findById(bike.getId()).orElseThrow().isClaimed()).isFalse();
    }

    @Test
    void prizesOffTheListCanBeGivenAndJoinTheWinnersPicks() {
        openDraw();
        lookUp(7);

        Details others = _get(Details.class);
        others.setOpened(true);
        _setValue(_get(Checkbox.class, spec -> spec.withLabel("Mug")), true);

        Participant reloaded = participants.findById(ann.getId()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Bike", "Book", "Mug");
        assertThat(_find(Checkbox.class)).extracting(Checkbox::getLabel).containsExactly("Bike", "Book", "Mug");
        assertThat(_get(Details.class).getSummaryText()).isEqualTo("Other available prizes (0)");
    }

    @Test
    void newPrizesCanBeAddedOnTheSpotAndHandedOver() {
        openDraw();
        lookUp(7);

        _setValue(_get(TextField.class, spec -> spec.withPlaceholder("New prize name")), "Surprise box");
        _click(_get(Button.class, spec -> spec.withText("Add & give to Ann")));

        Prize surprise = prizes.findAll().stream().filter(p -> p.getName().equals("Surprise box")).findFirst().orElseThrow();
        assertThat(surprise.isClaimedBy(ann)).isTrue();
        assertThat(_get(Checkbox.class, spec -> spec.withLabel("Surprise box")).getValue()).isTrue();

        _setValue(_get(TextField.class, spec -> spec.withPlaceholder("New prize name")), "Spare hat");
        _click(_get(Button.class, spec -> spec.withText("Add")));

        assertThat(prizes.findAll()).extracting(Prize::getName).contains("Spare hat");
        assertThat(_get(Details.class).getSummaryText()).isEqualTo("Other available prizes (2)");
    }

    @Test
    void blankPrizeNamesAreRefused() {
        openDraw();
        lookUp(7);

        TextField name = _get(TextField.class, spec -> spec.withPlaceholder("New prize name"));
        _setValue(name, "   ");
        _click(_get(Button.class, spec -> spec.withText("Add")));

        assertThat(name.isInvalid()).isTrue();
        assertThat(prizes.count()).isEqualTo(3);
    }
}
