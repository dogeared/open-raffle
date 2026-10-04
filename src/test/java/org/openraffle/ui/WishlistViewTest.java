package org.openraffle.ui;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.PollEvent;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.domain.TicketRange;
import org.openraffle.ui.participant.WishlistView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.expectNotifications;
import static org.assertj.core.api.Assertions.assertThat;

class WishlistViewTest extends KaribuTest {

    private Event fair;
    private Participant ann;

    private void openWishlist(String... prizeNames) {
        fair = event("Spring fair");
        for (String name : prizeNames) {
            prize(fair, name);
        }
        ann = participant(fair, "Ann", 1, 10);
        start();
        navigate("p/" + ann.getToken());
        _assertOne(WishlistView.class);
    }

    private static Button iconButton(String ariaLabel, int index) {
        return _find(Button.class, spec -> spec.withPredicate(b -> ariaLabel.equals(b.getAriaLabel().orElse("")))).get(index);
    }

    private static String savedLabel() {
        return _get(Span.class, spec -> spec.withPredicate(s -> s.getText().startsWith("Saved")
                || s.getText().startsWith("Unsaved") || s.getText().startsWith("Not saved"))).getText();
    }

    @Test
    void participantsPickRankAndSaveWithoutLoggingIn() {
        openWishlist("Bike", "Book", "Mug");

        assertThat(_get(H1.class).getText()).isEqualTo("Hi Ann!");
        assertThat(savedLabel()).isEqualTo("Not saved yet");
        assertThat(_find(Button.class, spec -> spec.withText("Add"))).hasSize(3);

        _click(_find(Button.class, spec -> spec.withText("Add")).get(2)); // Mug
        _click(_find(Button.class, spec -> spec.withText("Add")).get(0)); // Bike
        assertThat(savedLabel()).startsWith("Unsaved");
        _click(iconButton("Move up", 1));                                 // Bike above Mug
        _click(_get(Button.class, spec -> spec.withText("Save my wishlist")));

        expectNotifications("Saved! Good luck 🍀");
        assertThat(savedLabel()).startsWith("Saved ");
        Participant reloaded = participants.findByToken(ann.getToken()).orElseThrow();
        assertThat(reloaded.getWishlist()).extracting(Prize::getName).containsExactly("Bike", "Mug");
        assertThat(reloaded.getWishlistUpdatedAt()).isNotNull();

        _click(iconButton("Remove", 0));
        _click(_get(Button.class, spec -> spec.withText("Save my wishlist")));
        assertThat(participants.findByToken(ann.getToken()).orElseThrow().getWishlist())
                .extracting(Prize::getName).containsExactly("Mug");
    }

    @Test
    void unsavedChangesAreWrittenOnTheNextPoll() {
        openWishlist("Bike");
        _click(_get(Button.class, spec -> spec.withText("Add")));
        assertThat(participants.findByToken(ann.getToken()).orElseThrow().getWishlist()).isEmpty();

        UI ui = UI.getCurrent();
        assertThat(ui.getPollInterval()).isEqualTo(WishlistView.AUTOSAVE_INTERVAL_MS);
        ComponentUtil.fireEvent(ui, new PollEvent(ui, true));

        assertThat(participants.findByToken(ann.getToken()).orElseThrow().getWishlist()).hasSize(1);
        assertThat(savedLabel()).startsWith("Saved ");
    }

    @Test
    void ticketsAreListedOneRangePerLine() {
        openWishlist();
        assertThat(TicketRangeLabelTest.shown(_get(TicketRangeList.class))).isEqualTo("1 – 10");

        ann.setRanges(new ArrayList<>(List.of(TicketRange.of("987-001", "987-010"), TicketRange.of("12-05", "12-05"))));
        participants.save(ann);
        navigate("p/" + ann.getToken());

        assertThat(TicketRangeLabelTest.numbers(_get(TicketRangeList.class)))
                .containsExactly("12-05", "987-001", "987-010");
    }

    @Test
    void availablePrizesArePaginated() {
        openWishlist(IntStream.rangeClosed(1, 12).mapToObj(i -> String.format("Prize %02d", i)).toArray(String[]::new));

        assertThat(_find(Button.class, spec -> spec.withText("Add"))).hasSize(10);
        assertThat(_get(Span.class, spec -> spec.withText("1–10 of 12"))).isNotNull();

        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));

        assertThat(_find(Button.class, spec -> spec.withText("Add"))).hasSize(2);
        _click(_find(Button.class, spec -> spec.withText("Add")).get(0));
        assertThat(_get(Span.class, spec -> spec.withText("11–11 of 11"))).isNotNull();
    }

    @Test
    void badLinksAndFinishedRafflesAreExplained() {
        start();
        navigate("p/not-a-real-token");
        assertThat(_get(H1.class).getText()).isEqualTo("Hmm, that link isn't valid");

        Event over = event("Old fair");
        over.setDeletedAt(Instant.now());
        events.save(over);
        Participant old = participant(over, "Old Timer", 1, 1);
        navigate("p/" + old.getToken());
        assertThat(_get(H1.class).getText()).isEqualTo("This raffle is over");
        _assertNone(Button.class, spec -> spec.withText("Save my wishlist"));
    }

    @Test
    void theFooterIsOnTheWishlistPageToo() {
        openWishlist("Bike");

        assertThat(_get(AppFooter.class).getElement().getTextRecursively()).contains("dogeared");
    }
}
