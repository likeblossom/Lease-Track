package com.leasetrack.event.model;

import java.time.Instant;
import java.util.UUID;

public record TrackingSyncRequested(
        UUID eventId,
        Instant requestedAt,
        UUID deliveryAttemptId,
        String carrierCode,
        String trackingNumber,
        int attemptNumber) {
}
