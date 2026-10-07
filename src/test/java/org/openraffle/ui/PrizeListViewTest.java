package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.ui.pub.PrizeListView;

import java.time.Instant;
import java.util.stream.IntStream;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class PrizeListViewTest extends KaribuTest {

    @Test
    void anyoneCanReadTheEventsPrizesByItsNameWithNothingToPick() {
        Event fair = event("Carnage & Fun 29", "pat@example.com");
        Prize bike = prize(fair, "Bike");
        bike.setDescription("Red, 21 gears");
        prizes.save(bike);
        Prize mug = prize(fair, "Mug");
        Participant ann = participant(fair, "Ann", 1, 10);
        mug.setClaimedBy(ann);
        mug.setClaimedAt(Instant.now());
        prizes.save(mug);
        start(); // nobody logged in

        navigate("e/carnage-fun-29");

        _assertOne(PrizeListView.class);
        String text = _get(PrizeListView.class).getElement().getTextRecursively();
        assertThat(text).contains("Carnage & Fun 29", "Bike", "Red, 21 gears", "Mug");
        assertThat(_find(Span.class, spec -> spec.withText("Claimed"))).hasSize(1);
        _assertNone(Button.class, spec -> spec.withText("Add"));
        _assertNone(Button.class, spec -> spec.withText("Save my wishlist"));
        assertThat(_get(AppFooter.class).getElement().getTextRecursively()).contains("dogeared");
    }

    @Test
    void prizesWithAPictureShowItBesideTheirName() {
        Event fair = event("Spring fair", "pat@example.com");
        Prize bike = prize(fair, "Bike");
        bike.setImageFile("prize-1-0123456789abcdef.png");
        prizes.save(bike);
        prize(fair, "Mug");
        start();

        navigate("e/spring-fair");

        Image thumbnail = _get(Image.class);
        assertThat(thumbnail.getSrc()).isEqualTo("images/prize-1-0123456789abcdef.png");
        assertThat(thumbnail.getAlt()).contains("Bike");
        assertThat(thumbnail.getElement().getAttribute("role")).isEqualTo("button");

        // Clicking the little picture opens the big one.
        _click(thumbnail);

        Dialog dialog = _get(Dialog.class);
        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.getHeaderTitle()).isEqualTo("Bike");
        Image large = _get(dialog, Image.class);
        assertThat(large.getSrc()).isEqualTo("images/prize-1-0123456789abcdef.png");
        assertThat(large.getClassNames()).contains("prize-picture-large");
        _click(_get(dialog, Button.class, spec -> spec.withText("Close")));
        assertThat(dialog.isOpened()).isFalse();
    }

    @Test
    void theListIsPaginatedLikeTheWishlist() {
        Event fair = event("Spring fair", "pat@example.com");
        IntStream.rangeClosed(1, 12).forEach(i -> prize(fair, String.format("Prize %02d", i)));
        start();

        navigate("e/spring-fair");

        assertThat(_get(Span.class, spec -> spec.withText("1–10 of 12"))).isNotNull();
        assertThat(_get(PrizeListView.class).getElement().getTextRecursively()).contains("Prize 10").doesNotContain("Prize 11");
        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));
        assertThat(_get(Span.class, spec -> spec.withText("11–12 of 12"))).isNotNull();
        assertThat(_get(PrizeListView.class).getElement().getTextRecursively()).contains("Prize 11", "Prize 12");
    }

    @Test
    void unknownNamesAndDeletedEventsAreNotFound() {
        Event over = event("Old fair", "pat@example.com");
        over.setDeletedAt(Instant.now());
        events.save(over);
        start();

        navigate("e/old-fair");
        assertThat(_get(H1.class).getText()).isEqualTo("Hmm, we couldn't find that raffle");

        navigate("e/no-such-raffle");
        assertThat(_get(H1.class).getText()).isEqualTo("Hmm, we couldn't find that raffle");
    }

    @Test
    void anEventWithNoPrizesSaysSo() {
        event("Spring fair", "pat@example.com");
        start();

        navigate("e/spring-fair");

        assertThat(_get(Span.class, spec -> spec.withText("No prizes have been announced yet."))).isNotNull();
    }
}
