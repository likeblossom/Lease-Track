package com.leasetrack.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record RenewLeaseRequest(
        @NotNull LocalDate nextStartDate,
        @NotNull LocalDate nextEndDate,
        @NotNull @PositiveOrZero Long rentCents,
        LocalDate renewalDecisionDueDate,
        String notes) {
}
