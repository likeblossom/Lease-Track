package com.leasetrack.tracking;

import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tracking.provider", havingValue = "mock", matchIfMissing = true)
public class MockTrackingProvider implements TrackingProvider {

    private final Clock clock;

    public MockTrackingProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public TrackingSummary track(String trackingNumber) {
        boolean delivered = trackingNumber != null
                && trackingNumber.toUpperCase().contains("DELIVERED");
        return new TrackingSummary(
                trackingNumber,
                delivered ? "Delivered" : "In transit",
                delivered ? "DELIVERED" : "IN_TRANSIT",
                delivered,
                Instant.now(clock));
    }
}
