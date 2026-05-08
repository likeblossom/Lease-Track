package com.leasetrack.tracking;

public interface TrackingProvider {

    TrackingSummary track(String trackingNumber);
}
