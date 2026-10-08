package org.openraffle.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

@Entity
@Table(name = "prize")
public class Prize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Required; nullable in the schema only for rows that predate events (see Participant). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    private Event event;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Unused since 0.2.2 (prizes are listed alphabetically); kept because the column is NOT NULL. */
    @Column(nullable = false)
    private int sortOrder;

    /** The winner who took this prize during the draw, or null while it is still available. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "claimed_by_id")
    private Participant claimedBy;

    private Instant claimedAt;

    /** BoardGameGeek item this prize is, when the organizer linked one. */
    private Long bggId;

    /** The BGG item's name at the time it was linked, for display without another API call. */
    private String bggName;

    /** The BGG id the stored image was fetched for; differs from {@link #bggId} until refreshed. */
    private Long bggImageId;

    /** Stored image file name (see PrizeImageStore), or null when there is no picture. */
    private String imageFile;

    /** BGG's community average rating (1–10) as last fetched; ratings move slowly, so it is cached here. */
    private Double bggRating;

    /** When {@link #bggRating} was fetched; refreshed once it is older than a month. */
    private Instant bggRatingAt;

    /** How many people rated it on BGG; a handful means BGG shows the score in grey, unranked. */
    private Integer bggRatingCount;

    /** Organizers' own pictures, in the order they chose; the first is the primary one. */
    @OneToMany(mappedBy = "prize", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("position ASC")
    private List<PrizePicture> pictures = new ArrayList<>();

    /** How many pictures an organizer may upload for one prize. */
    public static final int MAX_PICTURES = 10;

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Participant getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(Participant claimedBy) {
        this.claimedBy = claimedBy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Long getBggId() {
        return bggId;
    }

    public void setBggId(Long bggId) {
        this.bggId = bggId;
    }

    public String getBggName() {
        return bggName;
    }

    public void setBggName(String bggName) {
        this.bggName = bggName;
    }

    public Long getBggImageId() {
        return bggImageId;
    }

    public void setBggImageId(Long bggImageId) {
        this.bggImageId = bggImageId;
    }

    public String getImageFile() {
        return imageFile;
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
    }

    public Double getBggRating() {
        return bggRating;
    }

    public void setBggRating(Double bggRating) {
        this.bggRating = bggRating;
    }

    public Integer getBggRatingCount() {
        return bggRatingCount;
    }

    public void setBggRatingCount(Integer bggRatingCount) {
        this.bggRatingCount = bggRatingCount;
    }

    public Instant getBggRatingAt() {
        return bggRatingAt;
    }

    public void setBggRatingAt(Instant bggRatingAt) {
        this.bggRatingAt = bggRatingAt;
    }

    public List<PrizePicture> getPictures() {
        return pictures;
    }

    /** The BGG box image, if one was downloaded. */
    public boolean hasBggImage() {
        return imageFile != null && !imageFile.isBlank();
    }

    /** The BGG box image's path, or null without one. */
    public String getBggImageUrl() {
        return hasBggImage() ? "images/" + imageFile : null;
    }

    /** Whether there is anything to show: an uploaded picture or the BGG box image. */
    public boolean hasImage() {
        return !pictures.isEmpty() || hasBggImage();
    }

    /** The primary picture's path: the first uploaded one, else the BGG box image; null without either. */
    public String getImageUrl() {
        return pictures.isEmpty() ? getBggImageUrl() : pictures.get(0).getUrl();
    }

    /** Every picture to show, primary first: the uploads in order, then the BGG box image. */
    public List<String> getGalleryUrls() {
        List<String> urls = new ArrayList<>();
        for (PrizePicture picture : pictures) {
            urls.add(picture.getUrl());
        }
        if (hasBggImage()) {
            urls.add(getBggImageUrl());
        }
        return urls;
    }

    public String getBggUrl() {
        return bggId == null ? null : "https://boardgamegeek.com/boardgame/" + bggId;
    }

    public boolean isClaimed() {
        return claimedBy != null;
    }

    public boolean isClaimedBy(Participant participant) {
        return claimedBy != null && claimedBy.equals(participant);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Prize other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
