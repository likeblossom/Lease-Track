package com.leasetrack.repository;

import com.leasetrack.domain.entity.DeliveryAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotice_IdOrderByAttemptNumberAsc(UUID noticeId);

    Optional<DeliveryAttempt> findByIdAndNotice_Id(UUID id, UUID noticeId);
}
