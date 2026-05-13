package com.leasetrack.dto.request;

import com.leasetrack.domain.enums.UnitStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreatePropertyUnitRequest(
        @NotBlank String unitLabel,
        @NotNull UnitStatus status,
        Integer bedrooms,
        BigDecimal bathrooms,
        Integer squareFeet,
        String currentTenantNames,
        Long currentRentCents) {
}
