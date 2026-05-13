package com.leasetrack.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PropertySummaryResponse(
        UUID id,
        String name,
        String address,
        long unitCount,
        long occupiedUnitCount,
        double occupancyRate,
        long activeNoticeCount,
        Instant createdAt,
        Instant updatedAt) {
}
