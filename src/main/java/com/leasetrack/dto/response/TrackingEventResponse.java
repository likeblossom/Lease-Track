package com.leasetrack.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TrackingEventResponse(
        UUID id,
        UUID deliveryAttemptId,
        String trackingNumber,
        String eventKey,
        String status,
        String statusCode,
        boolean delivered,
        Instant eventAt,
        Instant checkedAt,
        String errorMessage) {
}
