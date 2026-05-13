package com.leasetrack.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LeaseResponse(
        UUID id,
        String name,
        String propertyAddress,
        String tenantNames,
        String tenantEmail,
        String tenantPhone,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        UUID ownerUserId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<NoticeSummaryResponse> notices) {
}
