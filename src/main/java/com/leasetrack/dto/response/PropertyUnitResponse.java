package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.UnitStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PropertyUnitResponse(
        UUID id,
        UUID propertyId,
        String unitLabel,
        UnitStatus status,
        Integer bedrooms,
        BigDecimal bathrooms,
        Integer squareFeet,
        String currentTenantNames,
        Long currentRentCents,
        Instant createdAt,
        Instant updatedAt) {
}
