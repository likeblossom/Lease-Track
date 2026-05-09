package com.leasetrack.tracking;

public class TrackingProviderException extends RuntimeException {

    private final TrackingProviderErrorType errorType;
    private final Integer statusCode;

    public TrackingProviderException(TrackingProviderErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.statusCode = null;
    }

    public TrackingProviderException(TrackingProviderErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = null;
    }

    public TrackingProviderException(
            TrackingProviderErrorType errorType,
            Integer statusCode,
            String message,
            Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    public TrackingProviderErrorType getErrorType() {
        return errorType;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
