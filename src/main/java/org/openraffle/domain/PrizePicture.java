package org.openraffle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/**
 * A picture an organizer uploaded for a prize. The bytes live in the connected Google
 * Drive folder (the system of record) and in the local images directory (a cache); the
 * file name is minted by the app, never taken from the upload.
 */
@Entity
public class PrizePicture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prize_id")
    private Prize prize;

    /** 0-based place in the prize's list; the first is the primary picture. */
    @Column(nullable = false)
    private int position;

    /** Local cache name, e.g. prize-12-0123456789abcdef.jpg (see PrizeImageStore). */
    @Column(nullable = false, unique = true, length = 80)
    private String fileName;

    /** The file in Google Drive. */
    @Column(nullable = false, length = 128)
    private String driveFileId;

    @Column(nullable = false, length = 40)
    private String contentType;

    private Instant createdAt;

    /** Email of the organizer who uploaded it. */
    private String uploadedBy;

    public Long getId() {
        return id;
    }

    public Prize getPrize() {
        return prize;
    }

    public void setPrize(Prize prize) {
        this.prize = prize;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public void setDriveFileId(String driveFileId) {
        this.driveFileId = driveFileId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getUrl() {
        return "images/" + fileName;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PrizePicture other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
