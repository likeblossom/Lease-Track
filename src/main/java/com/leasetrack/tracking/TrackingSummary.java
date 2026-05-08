package com.leasetrack.tracking;

import java.time.Instant;

public record TrackingSummary(
        String trackingNumber,
        String status,
        String statusCode,
        boolean delivered,
        Instant eventAt) {
}
