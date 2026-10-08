package org.openraffle.repository;

import org.openraffle.domain.DriveConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriveConnectionRepository extends JpaRepository<DriveConnection, Long> {
}
