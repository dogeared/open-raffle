package org.openraffle.repository;

import org.openraffle.domain.PrizePicture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrizePictureRepository extends JpaRepository<PrizePicture, Long> {

    Optional<PrizePicture> findByFileName(String fileName);
}
