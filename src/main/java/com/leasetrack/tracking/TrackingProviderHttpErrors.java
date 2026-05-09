package com.leasetrack.tracking;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public final class TrackingProviderHttpErrors {

    private TrackingProviderHttpErrors() {
    }

    public static TrackingProviderException classify(String providerName, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        TrackingProviderErrorType type = switch (status) {
            case 401, 403 -> TrackingProviderErrorType.AUTHENTICATION;
            case 404 -> TrackingProviderErrorType.NOT_FOUND;
            case 429 -> TrackingProviderErrorType.RATE_LIMITED;
            default -> status >= 500
                    ? TrackingProviderErrorType.TRANSIENT
                    : TrackingProviderErrorType.PERMANENT;
        };
        return new TrackingProviderException(
                type,
                status,
                providerName + " tracking lookup failed with HTTP " + status,
                ex);
    }

    public static TrackingProviderException timeout(String providerName, ResourceAccessException ex) {
        return new TrackingProviderException(
                TrackingProviderErrorType.TRANSIENT,
                providerName + " tracking lookup timed out or could not connect",
                ex);
    }
}
