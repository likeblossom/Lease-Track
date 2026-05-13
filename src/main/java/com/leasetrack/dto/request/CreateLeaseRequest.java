package com.leasetrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateLeaseRequest(
        @NotBlank String name,
        @NotBlank String propertyAddress,
        @NotBlank String tenantNames,
        String tenantEmail,
        String tenantPhone,
        @NotNull LocalDate leaseStartDate,
        @NotNull LocalDate leaseEndDate,
        String notes) {
}
