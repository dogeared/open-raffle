package org.openraffle.service;

import org.junit.jupiter.api.Test;
import org.openraffle.domain.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({EventService.class, EventServiceTest.Users.class})
class EventServiceTest {

    @TestConfiguration
    static class Users {
        /** One bean, exposed by its concrete type so the test can flip roles, implementing CurrentUser. */
        @Bean
        StubCurrentUser currentUser() {
            return new StubCurrentUser().admin();
        }
    }

    @Autowired
    EventService eventService;

    @Autowired
    StubCurrentUser user;

    @Test
    void adminSeesEveryEventIncludingDeletedOnes() {
        user.admin();
        Event spring = eventService.create("Spring fair");
        Event autumn = eventService.create("Autumn fair");
        eventService.softDelete(autumn);

        assertThat(eventService.findAllForAdmin()).extracting(Event::getName).contains("Spring fair", "Autumn fair");
        assertThat(eventService.findAccessible()).containsExactly(spring);
    }

    @Test
    void organizerSeesOnlyActiveEventsListingTheirEmail() {
        user.admin();
        Event mine = eventService.create("Mine");
        eventService.setOrganizers(mine, List.of("  Pat@Example.com ", "", "other@example.com"));
        Event notMine = eventService.create("Not mine");
        Event deleted = eventService.create("Deleted");
        eventService.setOrganizers(deleted, List.of("pat@example.com"));
        eventService.softDelete(deleted);

        user.organizer("PAT@example.com");
        assertThat(eventService.findAccessible()).containsExactly(mine);
        assertThat(eventService.requireAccess(mine.getId())).isEqualTo(mine);
        assertThatThrownBy(() -> eventService.requireAccess(notMine.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> eventService.requireAccess(deleted.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> eventService.requireAccess(999_999L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void organizersCannotManageEvents() {
        user.admin();
        Event event = eventService.create("Fair");

        user.organizer("pat@example.com");
        assertThatThrownBy(() -> eventService.create("Another")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> eventService.softDelete(event)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(eventService::findAllForAdmin).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteIsSoftAndReversible() {
        user.admin();
        Event event = eventService.create("Fair");

        eventService.softDelete(event);
        assertThat(eventService.findById(event.getId())).get().matches(Event::isDeleted);
        assertThat(eventService.requireAccess(event.getId())).isEqualTo(event); // admins may still open it

        eventService.reinstate(event);
        assertThat(eventService.findById(event.getId())).get().matches(e -> !e.isDeleted());
    }

    @Test
    void namesThatShareAPublicLinkAreRejectedButDeletedEventsFreeTheirs() {
        user.admin();
        Event fair = eventService.create("Carnage & Fun 29");
        assertThat(fair.getSlug()).isEqualTo("carnage-fun-29");

        assertThatThrownBy(() -> eventService.create("Carnage Fun 29"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/e/carnage-fun-29");
        assertThatThrownBy(() -> eventService.create("!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public link");

        assertThat(eventService.findActiveBySlug("carnage-fun-29")).contains(fair);
        eventService.softDelete(fair);
        assertThat(eventService.findActiveBySlug("carnage-fun-29")).isEmpty();
        assertThat(eventService.create("Carnage Fun 29").getSlug()).isEqualTo("carnage-fun-29");
    }

    @Test
    void namesMustBeUniqueIgnoringCase() {
        user.admin();
        eventService.create("Spring Fair");

        assertThatThrownBy(() -> eventService.create("spring fair"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> eventService.create("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
