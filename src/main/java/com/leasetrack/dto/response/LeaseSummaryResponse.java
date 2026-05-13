package com.leasetrack.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseSummaryResponse(
        UUID id,
        String name,
        String propertyAddress,
        String tenantNames,
        LocalDate leaseStartDate,
        LocalDate leaseEndDate,
        long noticeCount,
        long openNoticeCount,
        Instant createdAt,
        Instant updatedAt) {
}
