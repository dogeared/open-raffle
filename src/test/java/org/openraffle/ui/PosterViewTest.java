package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.openraffle.ui.admin.PosterView;
import org.openraffle.ui.events.EventsView;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class PosterViewTest extends KaribuTest {

    @Test
    void showsTheEventNameTheQrCodeAndTheScanMessageWithNoAppChrome() {
        Event fair = event("Carnage & Fun 29", "pat@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events/" + fair.getId() + "/poster");

        _assertOne(PosterView.class);
        _assertNone(MainLayout.class);
        _assertNone(AppFooter.class);
        assertThat(_get(H1.class).getText()).isEqualTo("Carnage & Fun 29");
        Image qr = _get(Image.class);
        assertThat(qr.getSrc()).endsWith("prizes-carnage-fun-29.png");
        assertThat(qr.getAlt()).hasValueSatisfying(alt -> assertThat(alt).contains("Carnage & Fun 29"));
        assertThat(_get(Paragraph.class).getElement().getProperty("innerHTML")).isEqualTo("Scan for<br>available prizes!");
        assertThat(_get(Button.class, spec -> spec.withText("Print"))).isNotNull();
        assertThat(_get(Anchor.class, spec -> spec.withText("Back to prizes")).getHref()).isEqualTo("events/" + fair.getId());
        assertThat(_get(Anchor.class, spec -> spec.withText("Download QR PNG")).getElement().hasAttribute("download")).isTrue();
    }

    @Test
    void longNamesGetASmallerFontSoTheyStayOnOneLine() {
        Event shortName = event("Fair", "pat@example.com");
        Event longName = event("The Thirty-Second Annual Harvest Festival Raffle", "pat@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events/" + shortName.getId() + "/poster");
        String small = _get(H1.class).getStyle().get("font-size");
        navigate("events/" + longName.getId() + "/poster");
        String large = _get(H1.class).getStyle().get("font-size");

        assertThat(small).isEqualTo("min(13vmin, calc(92vw / 2.32))");
        assertThat(large).isEqualTo("min(13vmin, calc(92vw / 27.84))");
    }

    @Test
    void organizersNotOnTheEventAreSentBack() {
        Event fair = event("Spring fair", "other@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events/" + fair.getId() + "/poster");

        _assertNone(PosterView.class);
        _assertOne(EventsView.class);
    }
}
