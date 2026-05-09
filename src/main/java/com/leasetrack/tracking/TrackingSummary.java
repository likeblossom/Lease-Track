package com.leasetrack.tracking;

import java.time.Instant;
import java.util.List;

public record TrackingSummary(
        String trackingNumber,
        String status,
        String statusCode,
        boolean delivered,
        Instant eventAt,
        String rawProviderPayload,
        List<TrackingEvent> events) {

    public TrackingSummary(
            String trackingNumber,
            String status,
            String statusCode,
            boolean delivered,
            Instant eventAt,
            String rawProviderPayload) {
        this(
                trackingNumber,
                status,
                statusCode,
                delivered,
                eventAt,
                rawProviderPayload,
                List.of(new TrackingEvent(
                        eventKey(trackingNumber, statusCode, status, eventAt),
                        trackingNumber,
                        status,
                        statusCode,
                        delivered,
                        eventAt)));
    }

    public TrackingSummary {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static String eventKey(String trackingNumber, String statusCode, String status, Instant eventAt) {
        String timestamp = eventAt == null ? "unknown-time" : eventAt.toString();
        return String.join("|",
                safe(trackingNumber),
                safe(statusCode),
                safe(status),
                timestamp);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
