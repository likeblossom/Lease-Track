package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
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
        Instant createdAt,
        Instant updatedAt) {
}
