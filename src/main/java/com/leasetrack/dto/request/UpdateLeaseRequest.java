package com.leasetrack.dto.request;

import com.leasetrack.domain.enums.LeaseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateLeaseRequest(
        @NotBlank String name,
        @NotBlank String propertyAddress,
        UUID unitId,
        @NotBlank String tenantNames,
        String tenantEmail,
        String tenantPhone,
        @NotNull LocalDate leaseStartDate,
        @NotNull LocalDate leaseEndDate,
        @NotNull @PositiveOrZero Long rentCents,
        @PositiveOrZero Long securityDepositCents,
        LeaseStatus status,
        LocalDate renewalDecisionDueDate,
        String notes) {
}
