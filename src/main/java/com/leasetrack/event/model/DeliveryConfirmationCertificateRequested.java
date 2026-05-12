package com.leasetrack.event.model;

import java.time.Instant;
import java.util.UUID;

public record DeliveryConfirmationCertificateRequested(
        UUID eventId,
        Instant requestedAt,
        UUID deliveryAttemptId,
        String carrierCode,
        String trackingNumber,
        int attemptNumber) {
}
