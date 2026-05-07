package com.leasetrack.repository;

import com.leasetrack.domain.entity.DeliveryEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEvidenceRepository extends JpaRepository<DeliveryEvidence, UUID> {

    Optional<DeliveryEvidence> findByDeliveryAttempt_Id(UUID deliveryAttemptId);
}
