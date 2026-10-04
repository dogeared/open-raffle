package org.openraffle.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class MainLayoutTest extends KaribuTest {

    @Test
    void navShowsTheEventSectionOnlyInsideAnEvent() {
        Event fair = event("Spring fair", "pat@example.com");
        event("Autumn fair", "pat@example.com");
        loginAsOrganizer("pat@example.com");
        start();

        navigate("events");
        assertThat(_find(SideNavItem.class)).extracting(SideNavItem::getLabel).containsExactly("Events");

        navigate("events/" + fair.getId());
        assertThat(_find(SideNavItem.class)).extracting(SideNavItem::getLabel)
                .containsExactly("Events", "Spring fair", "Prizes", "Participants", "Reports", "Draw");
        _assertOne(Button.class, spec -> spec.withText("Log out"));
        assertThat(_get(AppFooter.class).getElement().getTextRecursively()).contains("version");
    }

    @Test
    void adminsAreMarkedInTheHeader() {
        loginAsAdmin();
        start();

        navigate("events");

        assertThat(_get(MainLayout.class).getElement().getTextRecursively()).contains("Raffle Admin (admin)");
    }
}
