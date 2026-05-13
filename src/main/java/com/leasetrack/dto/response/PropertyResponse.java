package com.leasetrack.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String postalCode,
        String country,
        UUID ownerUserId,
        String notes,
        long unitCount,
        long occupiedUnitCount,
        long vacantUnitCount,
        long activeNoticeCount,
        double occupancyRate,
        Instant createdAt,
        Instant updatedAt,
        List<PropertyUnitResponse> units) {
}
