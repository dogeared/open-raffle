package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.upload.Upload;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import org.openraffle.image.TestImages;
import org.junit.jupiter.api.Test;
import org.openraffle.bgg.BggItem;
import org.openraffle.bgg.FakeBggClient;
import org.openraffle.domain.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.openraffle.ui.admin.PosterView;
import org.openraffle.ui.admin.PrizesView;

import java.util.stream.IntStream;

import static com.github.mvysny.kaributesting.v10.GridKt._get;
import static com.github.mvysny.kaributesting.v10.GridKt._getCellComponent;
import static com.github.mvysny.kaributesting.v10.GridKt._getFormattedRow;
import static com.github.mvysny.kaributesting.v10.GridKt._size;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNoDialogs;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.NotificationsKt.getNotifications;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class PrizesViewTest extends KaribuTest {

    @Autowired
    FakeBggClient bgg;

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
    void theQrCodeBesideTheHeadingOpensThePrintablePoster() {
        Event fair = openPrizes();

        Image small = _get(Image.class, spec -> spec.withPredicate(i -> i.getAlt().orElse("").contains("public prize list")));
        assertThat(small.getSrc()).endsWith("prizes-spring-fair.png");

        _click(small);

        _assertOne(PosterView.class);
        assertThat(_get(H1.class).getText()).isEqualTo(fair.getName());
    }

    @Test
    void theEditorLooksGamesUpOnBggFillsTheNameAndSavesThePicture() {
        Event fair = openPrizes();
        _click(_get(Button.class, spec -> spec.withText("Add prize")));

        @SuppressWarnings("unchecked")
        ComboBox<BggItem> game = _get(ComboBox.class, spec -> spec.withLabel("BoardGameGeek"));
        assertThat(game.isEnabled()).isTrue();
        assertThat(game.getHelperText()).contains("box image");
        _setValue(game, new BggItem(13, "CATAN", 1995));
        assertThat(_get(TextField.class, spec -> spec.withLabel("Name")).getValue()).isEqualTo("CATAN");
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Catan (base game)");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        Prize saved = prizes.findAllByEventAlphabetically(fair).get(0);
        assertThat(saved.getName()).isEqualTo("Catan (base game)");
        assertThat(saved.getBggId()).isEqualTo(13L);
        assertThat(saved.getBggName()).isEqualTo("CATAN");
        assertThat(saved.hasImage()).isTrue();
        // The grid shows the picture, and the editor shows it with the link preselected.
        Image thumbnail = _getCellComponent(grid(), 0, "picture") instanceof Image i ? i : null;
        assertThat(thumbnail).isNotNull();
        assertThat(thumbnail.getSrc()).isEqualTo(saved.getImageUrl());
        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 0, "actions");
        _click((Button) actions.getComponentAt(0));
        @SuppressWarnings("unchecked")
        ComboBox<BggItem> again = _get(ComboBox.class, spec -> spec.withLabel("BoardGameGeek"));
        assertThat(again.getValue().id()).isEqualTo(13L);
        assertThat(again.getValue().name()).isEqualTo("CATAN");
    }

    @Test
    void withoutAnApiKeyTheBggFieldIsOffAndExplainsWhy() {
        bgg.enabled = false;
        openPrizes();
        _click(_get(Button.class, spec -> spec.withText("Add prize")));

        ComboBox<?> game = _get(ComboBox.class, spec -> spec.withLabel("BoardGameGeek"));

        assertThat(game.isEnabled()).isFalse();
        assertThat(game.getHelperText()).contains("BGG_API_KEY");
    }

    @Test
    void picturesCanBeUploadedOrderedAndRemovedInTheEditorOnceDriveIsConnected() throws Exception {
        connectDrive();
        Event fair = openPrizes();
        _click(_get(Button.class, spec -> spec.withText("Add prize")));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Bike");
        PrizesView.PicturesEditor pictures = _get(PrizesView.PicturesEditor.class);
        assertThat(pictures.getElement().getTextRecursively()).contains("No pictures yet");
        _assertOne(Upload.class);

        // The first upload saves the new prize, then attaches the picture.
        receive(pictures, "front.jpg", "image/jpeg", TestImages.jpeg(400, 300));
        receive(pictures, "back.png", "image/png", TestImages.png(200, 200));

        Prize saved = prizes.findAllByEventAlphabetically(fair).get(0);
        assertThat(saved.getName()).isEqualTo("Bike");
        assertThat(saved.getPictures()).hasSize(2);
        assertThat(pictures.getElement().getTextRecursively()).contains("Primary picture").contains("Picture 2");
        assertThat(_find(pictures, Image.class)).hasSize(2);

        // Move the second up: it becomes the primary.
        _click(_find(pictures, Button.class, spec -> spec.withPredicate(b -> "Move up".equals(b.getAriaLabel().orElse("")))).get(1));
        saved = prizes.findById(saved.getId()).orElseThrow();
        assertThat(saved.getPictures().get(0).getFileName()).endsWith(".png");

        // Remove the primary: the other takes over.
        _click(_find(pictures, Button.class, spec -> spec.withPredicate(b -> "Remove picture".equals(b.getAriaLabel().orElse("")))).get(0));
        saved = prizes.findById(saved.getId()).orElseThrow();
        assertThat(saved.getPictures()).hasSize(1);
        assertThat(saved.getPictures().get(0).getFileName()).endsWith(".jpg");

        // A bad file is explained and nothing changes.
        receive(pictures, "evil.svg", "image/svg+xml", "<svg/>".getBytes());
        assertThat(getNotifications()).extracting(n -> n.getElement().getProperty("text"))
                .anySatisfy(text -> assertThat(text).contains("Only JPEG"));
        assertThat(prizes.findById(saved.getId()).orElseThrow().getPictures()).hasSize(1);

        _click(_get(Button.class, spec -> spec.withText("Save")));
        _assertNoDialogs();
        assertThat(_getCellComponent(grid(), 0, "picture")).isInstanceOf(Image.class);
    }

    /** An upload arrives on a request thread and updates the UI through UI.access, which Karibu runs on the next round trip. */
    private static void receive(PrizesView.PicturesEditor pictures, String name, String type, byte[] bytes) {
        pictures.receive(name, type, bytes);
        MockVaadin.INSTANCE.clientRoundtrip();
    }

    @Test
    void withoutDriveTheEditorSaysWhereToConnectInsteadOfOfferingUploads() {
        openPrizes();
        _click(_get(Button.class, spec -> spec.withText("Add prize")));

        PrizesView.PicturesEditor pictures = _get(PrizesView.PicturesEditor.class);

        _assertNone(Upload.class);
        assertThat(pictures.getElement().getTextRecursively()).contains("Connect Google Drive under Settings");
    }

    @Test
    void aNewPrizeNeedsANameBeforeItsFirstPicture() {
        connectDrive();
        openPrizes();
        _click(_get(Button.class, spec -> spec.withText("Add prize")));

        receive(_get(PrizesView.PicturesEditor.class), "a.png", "image/png", TestImages.png(10, 10));

        assertThat(getNotifications()).extracting(n -> n.getElement().getProperty("text"))
                .anySatisfy(text -> assertThat(text).contains("Enter a name"));
        assertThat(prizes.count()).isZero();
    }

    @Test
    void theEditorCarriesBggsPoweredByBadgeUnderTheLookup() {
        openPrizes();
        _click(_get(Button.class, spec -> spec.withText("Add prize")));

        Image badge = _get(Image.class, spec -> spec.withPredicate(i -> i.getAlt().orElse("").contains("Powered by BoardGameGeek")));
        assertThat(badge.getSrc()).isEqualTo("img/bgg-powered-by.png");
        Anchor link = (Anchor) badge.getParent().orElseThrow();
        assertThat(link.getHref()).isEqualTo("https://boardgamegeek.com");
        assertThat(link.getTarget()).contains("_blank");
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
        assertThat(_getFormattedRow(grid(), 0)).containsSubsequence("1", "Bike");
        assertThat(_getFormattedRow(grid(), 1)).containsSubsequence("2", "mug");

        HorizontalLayout actions = (HorizontalLayout) _getCellComponent(grid(), 1, "actions");
        _click((Button) actions.getComponentAt(0));
        _setValue(_get(TextField.class, spec -> spec.withLabel("Name")), "Apple");
        _click(_get(Button.class, spec -> spec.withText("Save")));

        assertThat(_getFormattedRow(grid(), 0)).containsSubsequence("1", "Apple");
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
        assertThat(_getFormattedRow(grid(), 9)).containsSubsequence("10", "Prize 10");
        assertThat(_get(Span.class, spec -> spec.withText("1–10 of 14"))).isNotNull();

        _click(_get(Button.class, spec -> spec.withPredicate(b -> "Next page".equals(b.getAriaLabel().orElse("")))));

        assertThat(_size(grid())).isEqualTo(4);
        assertThat(_getFormattedRow(grid(), 0)).containsSubsequence("11", "Prize 11");
        assertThat(_get(Span.class, spec -> spec.withText("11–14 of 14"))).isNotNull();

        _setValue(_get(Select.class), 25);

        assertThat(_size(grid())).isEqualTo(14);
        assertThat(_get(Span.class, spec -> spec.withText("1–14 of 14"))).isNotNull();
    }
}
