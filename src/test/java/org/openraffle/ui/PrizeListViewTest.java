package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
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

import static com.github.mvysny.kaributesting.v10.BasicUtilsKt._fireDomEvent;
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
        bike.setBggId(13L);
        bike.setBggRating(7.09005);
        bike.setBggRatingCount(144735);
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
        assertThat(dialog.isModal()).isTrue();
        Image large = _get(dialog, Image.class);
        assertThat(large.getSrc()).isEqualTo("images/prize-1-0123456789abcdef.png");
        assertThat(large.getClassNames()).contains("prize-picture-large");
        // BGG's rating sits on the picture, coloured like BGG colours a 7 (light blue), linking to the game.
        Anchor rating = _get(dialog, Anchor.class, spec -> spec.withClasses("bgg-rating"));
        assertThat(rating.getElement().getTextRecursively()).isEqualTo("7.1");
        assertThat(rating.getClassNames()).contains("bgg-rating-7");
        assertThat(rating.getElement().getAttribute("title")).isEqualTo("BoardGameGeek community rating 7.1 of 10 from 144735 ratings");
        assertThat(rating.getHref()).isEqualTo("https://boardgamegeek.com/boardgame/13");
        _click(_get(dialog, Button.class, spec -> spec.withText("Close")));
        assertThat(dialog.isOpened()).isFalse();
    }

    @Test
    void hoveringAThumbnailPeeksAtThePictureUntilThePointerLeaves() {
        Event fair = event("Spring fair", "pat@example.com");
        Prize bike = prize(fair, "Bike");
        bike.setImageFile("prize-1-0123456789abcdef.png");
        prizes.save(bike);
        start();
        navigate("e/spring-fair");
        Image thumbnail = _get(Image.class);

        hover(thumbnail);

        Dialog peek = _get(Dialog.class);
        assertThat(peek.isOpened()).isTrue();
        assertThat(peek.isModal()).isFalse();
        _assertNone(peek, Button.class, spec -> spec.withText("Close"));
        assertThat(_get(peek, Image.class).getSrc()).isEqualTo("images/prize-1-0123456789abcdef.png");

        leave(thumbnail);
        assertThat(peek.isOpened()).isFalse();

        // The picture opens over the thumbnail: the pointer "leaves" the thumbnail for the picture,
        // and the peek must stay until it leaves the picture itself.
        hover(thumbnail);
        Dialog covering = _get(Dialog.class, spec -> spec.withPredicate(Dialog::isOpened));
        _fireDomEvent(frame(covering), "mouseenter", tools.jackson.databind.json.JsonMapper.shared().createObjectNode());
        leave(thumbnail);
        assertThat(covering.isOpened()).isTrue();
        _fireDomEvent(frame(covering), "mouseleave", tools.jackson.databind.json.JsonMapper.shared().createObjectNode());
        assertThat(covering.isOpened()).isFalse();

        // Hover then click: the peek gives way to the pinned, modal dialog, which a leave does not close.
        hover(thumbnail);
        _click(thumbnail);
        Dialog pinned = _get(Dialog.class, spec -> spec.withPredicate(Dialog::isOpened));
        assertThat(pinned.isModal()).isTrue();
        leave(thumbnail);
        assertThat(pinned.isOpened()).isTrue();
    }

    /** The mouseenter listener is debounced, so the client sends it with a trailing phase. */
    private static void hover(Image thumbnail) {
        var data = tools.jackson.databind.json.JsonMapper.shared().createObjectNode();
        data.put(com.vaadin.flow.shared.JsonConstants.EVENT_DATA_PHASE, com.vaadin.flow.dom.DebouncePhase.TRAILING.getIdentifier());
        _fireDomEvent(thumbnail, "mouseenter", data);
    }

    /**
     * The thumbnail's mouseleave is debounced too, and filtered on the client: the browser
     * only sends it when the pointer is not inside a picture's box, and reports the filter's
     * result with the event. This is such a leave.
     */
    private static void leave(Image thumbnail) {
        var data = tools.jackson.databind.json.JsonMapper.shared().createObjectNode();
        data.put(com.vaadin.flow.shared.JsonConstants.EVENT_DATA_PHASE, com.vaadin.flow.dom.DebouncePhase.TRAILING.getIdentifier());
        data.put(PrizeThumbnail.POINTER_NOT_ON_PICTURE, true);
        _fireDomEvent(thumbnail, "mouseleave", data);
    }

    private static Div frame(Dialog dialog) {
        return _get(dialog, Div.class, spec -> spec.withClasses("prize-picture-frame"));
    }

    @Test
    void ratingColoursFollowBggsScale() {
        assertThat(BggRatingBadge.tier(9.97, 5000)).isEqualTo("10");  // shows 10.0
        assertThat(BggRatingBadge.tier(8.61406, 5000)).isEqualTo("8");
        assertThat(BggRatingBadge.tier(7.96, 5000)).isEqualTo("8");   // shows 8.0, coloured as an 8
        assertThat(BggRatingBadge.tier(7.57046, 5000)).isEqualTo("7"); // Dead Cells: light blue
        assertThat(BggRatingBadge.tier(6.2, 5000)).isEqualTo("6");
        assertThat(BggRatingBadge.tier(1.04, 5000)).isEqualTo("1");
        assertThat(BggRatingBadge.tier(8.9, 12)).isEqualTo("unranked"); // too few ratings: grey
        assertThat(BggRatingBadge.tier(8.9, null)).isEqualTo("8");      // count unknown: trust the score
        assertThat(BggRatingBadge.format(7.09005)).isEqualTo("7.1");
        Prize unrated = new Prize();
        unrated.setBggId(13L);
        assertThat(BggRatingBadge.of(unrated)).isNull();
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
