package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.ui.admin.PrizesView;

import java.util.stream.IntStream;

import static com.github.mvysny.kaributesting.v10.GridKt._get;
import static com.github.mvysny.kaributesting.v10.GridKt._getCellComponent;
import static com.github.mvysny.kaributesting.v10.GridKt._getFormattedRow;
import static com.github.mvysny.kaributesting.v10.GridKt._size;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNoDialogs;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class PrizesViewTest extends KaribuTest {

    @SuppressWarnings("unchecked")
    private static Grid<Prize> grid() {
        return _get(Grid.class);
    }

    private Event openPrizes() {
        Event fair = event("Spring fair", "pat@example.com");
        loginAsOrganizer("pat@example.com");
        start();
        navigate("events/" + fair.getId());
        _assertOne(PrizesView.class);
        return fair;
    }

    @Test
    void theToolbarLinksToTheEventsPublicPrizeListInANewTab() {
        openPrizes();

        Anchor publicList = _get(Anchor.class, spec -> spec.withPredicate(a -> a.getHref().startsWith("e/")));

        assertThat(publicList.getHref()).isEqualTo("e/spring-fair");
        assertThat(publicList.getTarget()).contains("_blank");
        assertThat(publicList.getElement().getTextRecursively()).contains("Public list");
    }

    @Test
    void aQrCodeBesideTheHeadingOpensABigOneForThePublicList() {
        openPrizes();

        Image small = _get(Image.class, spec -> spec.withPredicate(i -> i.getAlt().orElse("").contains("public prize list")));
        assertThat(small.getSrc()).endsWith("prizes-spring-fair.png");

        _click(small);

        Dialog dialog = _get(Dialog.class, spec -> spec.withPredicate(d -> "Spring fair".equals(d.getHeaderTitle())));
        assertThat(dialog.isOpened()).isTrue();
        Anchor link = _get(dialog, Anchor.class, spec -> spec.withPredicate(a -> a.getHref().contains("/e/")));
        assertThat(link.getHref()).endsWith("/e/spring-fair").startsWith("http");
        assertThat(_get(dialog, Image.class).getAlt()).hasValueSatisfying(alt -> assertThat(alt).contains("Spring fair"));
        assertThat(_get(dialog, Anchor.class, spec -> spec.withText("Download PNG")).getElement().hasAttribute("download")).isTrue();
    }

    @Test
    void prizesAreAddedEditedAndListedAlphabetically() {
        Event fair = openPrizes();
        prize(fair, "mug");
        navigate("events/" + fair.getId() + "/participants");
        navigate("events/" + fair.getId());

        _click(_get(Button.class, spec -> spec.withText("Add prize")));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Bike");
        _click(_get(Button.class, spec -> spec.withText("Save")));
        _assertNoDialogs();

        assertThat(_size(grid())).isEqualTo(2);
        assertThat(_getFormattedRow(grid(), 0)).startsWith("1", "Bike");
        assertThat(_getFormattedRow(grid(), 1)).startsWith("2", "mug");

        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 1, "actions");
        _click((Button) actions.getComponentAt(0));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Apple");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(_getFormattedRow(grid(), 0)).startsWith("1", "Apple");
        assertThat(prizes.count()).isEqualTo(2);
    }

    @Test
    void deletingAPrizeAsksFirst() {
        Event fair = openPrizes();
        prize(fair, "Bike");
        navigate("events/" + fair.getId() + "/participants");
        navigate("events/" + fair.getId());

        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 0, "actions");
        _click((Button) actions.getComponentAt(1));
        confirm(_get(ConfirmDialog.class));

        assertThat(prizes.count()).isZero();
        assertThat(_size(grid())).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void longListsArePaginatedTenAtATimeWithAChooser() {
        Event fair = openPrizes();
        IntStream.rangeClosed(1, 14).forEach(i -> prize(fair, String.format("Prize %02d", i)));
        navigate("events/" + fair.getId() + "/participants");
        navigate("events/" + fair.getId());

        assertThat(_size(grid())).isEqualTo(10);
        assertThat(_getFormattedRow(grid(), 9)).startsWith("10", "Prize 10");
        assertThat(_get(Span.class, spec -> spec.withText("1–10 of 14"))).isNotNull();

        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));

        assertThat(_size(grid())).isEqualTo(4);
        assertThat(_getFormattedRow(grid(), 0)).startsWith("11", "Prize 11");
        assertThat(_get(Span.class, spec -> spec.withText("11–14 of 14"))).isNotNull();

        _setValue(_get(Select.class), 25);

        assertThat(_size(grid())).isEqualTo(14);
        assertThat(_get(Span.class, spec -> spec.withText("1–14 of 14"))).isNotNull();
    }
}
