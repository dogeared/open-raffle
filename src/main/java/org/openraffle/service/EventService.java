package org.openraffle.service;

import org.openraffle.domain.Event;
import org.openraffle.repository.EventRepository;
import org.openraffle.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventService {

    private final EventRepository events;
    private final CurrentUser currentUser;

    public EventService(EventRepository events, CurrentUser currentUser) {
        this.events = events;
        this.currentUser = currentUser;
    }

    /** Every event, deleted ones included, newest first. Admins only. */
    @Transactional(readOnly = true)
    public List<Event> findAllForAdmin() {
        requireAdmin();
        return events.findAllByOrderByCreatedAtDesc();
    }

    /** The active events the current user may run: all of them for an admin. */
    @Transactional(readOnly = true)
    public List<Event> findAccessible() {
        if (currentUser.isAdmin()) {
            return events.findAllByDeletedAtIsNullOrderByNameAsc();
        }
        return currentUser.email()
                .map(events::findAllByDeletedAtIsNullAndOrganizerEmailsContainsOrderByNameAsc)
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Optional<Event> findById(Long id) {
        return id == null ? Optional.empty() : events.findById(id);
    }

    /**
     * The event, if the current user may work in it: admins always, organizers only when
     * listed on an active event.
     *
     * @throws AccessDeniedException otherwise (including for unknown ids, so the id cannot
     *                               be used to probe which events exist)
     */
    @Transactional(readOnly = true)
    public Event requireAccess(Long id) {
        Event event = findById(id).orElseThrow(() -> new AccessDeniedException("No such event"));
        if (currentUser.isAdmin()) {
            return event;
        }
        boolean allowed = !event.isDeleted()
                && currentUser.email().map(event::hasOrganizer).orElse(false);
        if (!allowed) {
            throw new AccessDeniedException("Not an organizer of this event");
        }
        return event;
    }

    public Event create(String name) {
        requireAdmin();
        Event event = new Event();
        event.setName(name);
        return save(event);
    }

    public Event save(Event event) {
        requireAdmin();
        if (event.getName() == null || event.getName().isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        events.findByNameIgnoreCase(event.getName())
                .filter(other -> !other.equals(event))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("An event named \"" + other.getName() + "\" already exists");
                });
        if (event.getSlug().isEmpty()) {
            throw new IllegalArgumentException("Event name needs at least one letter or digit, for its public link");
        }
        events.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(other -> !other.equals(event) && other.getSlug().equals(event.getSlug()))
                .findFirst()
                .ifPresent(other -> {
                    throw new IllegalArgumentException("\"" + other.getName() + "\" already uses the public link /e/" + other.getSlug());
                });
        return events.save(event);
    }

    /**
     * The active event whose name gives this public-URL slug, for the login-free prize list.
     * Deleted events are not found, so an old link stops working when the raffle is over.
     */
    @Transactional(readOnly = true)
    public Optional<Event> findActiveBySlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return Optional.empty();
        }
        return events.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(event -> event.getSlug().equals(slug))
                .findFirst();
    }

    public Event setOrganizers(Event event, Collection<String> emails) {
        requireAdmin();
        Set<String> normalised = emails.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        event.setOrganizerEmails(normalised);
        return events.save(event);
    }

    public Event softDelete(Event event) {
        requireAdmin();
        event.setDeletedAt(Instant.now());
        return events.save(event);
    }

    public Event reinstate(Event event) {
        requireAdmin();
        event.setDeletedAt(null);
        return events.save(event);
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Admins only");
        }
    }
}
