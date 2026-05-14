package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.LeaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LeaseResponse(
        UUID id,
        String name,
        String propertyAddress,
        UUID unitId,
        String unitLabel,
        String tenantNames,
        String tenantEmail,
        String tenantPhone,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        Long rentCents,
        Long securityDepositCents,
        LeaseStatus status,
        LocalDate renewalDecisionDueDate,
        Instant terminatedAt,
        UUID ownerUserId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<LeaseEventResponse> timeline,
        List<NoticeSummaryResponse> notices) {
}
