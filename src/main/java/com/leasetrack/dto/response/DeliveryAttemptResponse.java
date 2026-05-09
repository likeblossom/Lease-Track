package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.TrackingSyncStatus;
import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        int attemptNumber,
        DeliveryMethod deliveryMethod,
        DeliveryAttemptStatus status,
        Instant sentAt,
        Instant deliveredAt,
        Instant deadlineAt,
        TrackingSyncStatus trackingSyncStatus,
        Instant lastTrackingCheckedAt,
        boolean deadlineReminderSent,
        Instant createdAt,
        Instant updatedAt) {
}
