package com.leasetrack.tracking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tracking.provider", havingValue = "canada-post")
public class CanadaPostTrackingProvider implements TrackingProvider {

    @Override
    public TrackingSummary track(String trackingNumber) {
        throw new UnsupportedOperationException("Canada Post tracking integration is not implemented yet");
    }
}
