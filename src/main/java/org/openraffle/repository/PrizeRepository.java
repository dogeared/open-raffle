package org.openraffle.repository;

import org.openraffle.domain.Event;
import org.openraffle.domain.Participant;
import org.openraffle.domain.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PrizeRepository extends JpaRepository<Prize, Long> {

    @Query("select p from Prize p where p.event = :event order by lower(p.name) asc, p.id asc")
    List<Prize> findAllByEventAlphabetically(@Param("event") Event event);

    List<Prize> findAllByClaimedBy(Participant participant);

    List<Prize> findAllByEventAndClaimedByIsNotNullOrderByClaimedAtDesc(Event event);

    Optional<Prize> findByImageFile(String imageFile);

    /**
     * Linked prizes whose BGG rating has never been fetched, was fetched before {@code before},
     * or predates the rating count (so the count gets filled in once).
     */
    @Query("select p from Prize p where p.bggId is not null and (p.bggRatingAt is null or p.bggRatingAt < :before or p.bggRatingCount is null)")
    List<Prize> findAllWithStaleRating(@Param("before") Instant before);

    long countByEventIsNull();

    @Modifying
    @Query("update Prize p set p.event = :event where p.event is null")
    int attachOrphansTo(@Param("event") Event event);
}
