package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.LeaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseSummaryResponse(
        UUID id,
        String name,
        String propertyAddress,
        UUID unitId,
        String unitLabel,
        String tenantNames,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        Long rentCents,
        LeaseStatus status,
        LocalDate renewalDecisionDueDate,
        long noticeCount,
        long openNoticeCount,
        Instant createdAt,
        Instant updatedAt) {
}
