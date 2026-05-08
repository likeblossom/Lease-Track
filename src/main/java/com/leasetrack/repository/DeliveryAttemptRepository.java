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

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotice_IdOrderByAttemptNumberAsc(UUID noticeId);

    Optional<DeliveryAttempt> findByIdAndNotice_Id(UUID id, UUID noticeId);

    List<DeliveryAttempt> findByDeliveryMethodAndStatusIn(
            DeliveryMethod deliveryMethod,
            Collection<DeliveryAttemptStatus> statuses);

    List<DeliveryAttempt> findByDeadlineAtBetweenAndDeadlineReminderSentFalseAndStatusIn(
            Instant deadlineStart,
            Instant deadlineEnd,
            Collection<DeliveryAttemptStatus> statuses);
}
