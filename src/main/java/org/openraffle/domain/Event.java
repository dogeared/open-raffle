package org.openraffle.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A raffle. Participants, prizes and the draw all belong to exactly one event. Events are
 * never hard-deleted: {@link #deletedAt} hides them, and an admin can reinstate them.
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant deletedAt;

    /** This event's subfolder in the connected Google Drive, created on its first upload. */
    private String driveFolderId;

    /** The connection's root folder the subfolder was created in; a reconnect to another account starts over. */
    private String driveFolderRoot;

    /** Lower-cased emails of the organizers allowed to run this event; admins see every event. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_organizer", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "email", nullable = false)
    private Set<String> organizerEmails = new LinkedHashSet<>();

    public Long getId() {
        return id;
    }

    /** The name as it appears in the event's public URL, e.g. "carnage-fun-29". */
    public String getSlug() {
        return Slug.of(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public void setDriveFolderId(String driveFolderId) {
        this.driveFolderId = driveFolderId;
    }

    public String getDriveFolderRoot() {
        return driveFolderRoot;
    }

    public void setDriveFolderRoot(String driveFolderRoot) {
        this.driveFolderRoot = driveFolderRoot;
    }

    /** The subfolder's name in Drive: the event's name, plus its id so two alike names stay apart. */
    public String getDriveFolderName() {
        return name + " (#" + id + ")";
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Set<String> getOrganizerEmails() {
        return organizerEmails;
    }

    public void setOrganizerEmails(Set<String> organizerEmails) {
        this.organizerEmails = organizerEmails;
    }

    public boolean hasOrganizer(String email) {
        return email != null && organizerEmails.contains(email.toLowerCase());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Event other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
