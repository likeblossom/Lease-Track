package com.leasetrack.tracking;

import java.util.Optional;

public interface TrackingProvider {

    String carrierCode();

    default boolean supportsCarrier(String carrier) {
        return carrierCode().equals(TrackingProviderRegistry.normalizeCarrierCode(carrier));
    }

    default Optional<String> parseTrackingNumber(String trackingUrl) {
        return Optional.empty();
    }

    default Optional<DeliveryConfirmationCertificate> fetchDeliveryConfirmationCertificate(String trackingNumber) {
        return Optional.empty();
    }

    TrackingSummary track(String trackingNumber);
}
