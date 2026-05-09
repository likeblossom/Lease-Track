package com.leasetrack.repository;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotice_IdOrderByAttemptNumberAsc(UUID noticeId);

    Optional<DeliveryAttempt> findByIdAndNotice_Id(UUID id, UUID noticeId);

    List<DeliveryAttempt> findByDeliveryMethodAndStatusIn(
            DeliveryMethod deliveryMethod,
            Collection<DeliveryAttemptStatus> statuses);

    @Query("""
            select a.id
            from DeliveryAttempt a
            join a.evidence e
            where a.deliveryMethod = :deliveryMethod
              and a.status in :statuses
              and e.trackingNumber is not null
              and e.carrierCode is not null
              and (a.trackingNextCheckAt is null or a.trackingNextCheckAt <= :now)
            """)
    List<UUID> findTrackingSyncCandidateIds(
            @Param("deliveryMethod") DeliveryMethod deliveryMethod,
            @Param("statuses") Collection<DeliveryAttemptStatus> statuses,
            @Param("now") Instant now);

    List<DeliveryAttempt> findByDeadlineAtBetweenAndDeadlineReminderSentFalseAndStatusIn(
            Instant deadlineStart,
            Instant deadlineEnd,
            Collection<DeliveryAttemptStatus> statuses);
}
