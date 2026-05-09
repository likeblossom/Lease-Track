package com.leasetrack.tracking;

import java.time.Instant;

public record TrackingEvent(
        String eventKey,
        String trackingNumber,
        String status,
        String statusCode,
        boolean delivered,
        Instant eventAt) {
}
