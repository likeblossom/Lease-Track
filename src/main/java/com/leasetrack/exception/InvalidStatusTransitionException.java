package com.leasetrack.exception;

import com.leasetrack.domain.enums.DeliveryAttemptStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(DeliveryAttemptStatus currentStatus, DeliveryAttemptStatus targetStatus) {
        super("Invalid delivery attempt status transition: " + currentStatus + " -> " + targetStatus);
    }
}
