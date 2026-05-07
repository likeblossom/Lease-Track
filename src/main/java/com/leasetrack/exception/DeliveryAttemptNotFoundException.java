package com.leasetrack.exception;

import java.util.UUID;

public class DeliveryAttemptNotFoundException extends RuntimeException {

    public DeliveryAttemptNotFoundException(UUID noticeId, UUID deliveryAttemptId) {
        super("Delivery attempt not found: " + deliveryAttemptId + " for notice: " + noticeId);
    }
}
