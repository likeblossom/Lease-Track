package com.leasetrack.dto.request;

import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryAttemptStatusRequest(@NotNull DeliveryAttemptStatus status) {
}
