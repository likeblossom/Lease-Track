package com.leasetrack.mapper;

import com.leasetrack.domain.entity.Property;
import com.leasetrack.domain.entity.PropertyUnit;
import com.leasetrack.domain.enums.UnitStatus;
import com.leasetrack.dto.response.PropertyResponse;
import com.leasetrack.dto.response.PropertySummaryResponse;
import com.leasetrack.dto.response.PropertyUnitResponse;
import org.springframework.stereotype.Component;

@Component
public class PropertyMapper {

    public PropertySummaryResponse toSummaryResponse(Property property) {
        long unitCount = property.getUnits().size();
        long occupiedUnitCount = occupiedUnitCount(property);
        return new PropertySummaryResponse(
                property.getId(),
                property.getName(),
                formattedAddress(property),
                unitCount,
                occupiedUnitCount,
                occupancyRate(unitCount, occupiedUnitCount),
                0,
                property.getCreatedAt(),
                property.getUpdatedAt());
    }

    public PropertyResponse toResponse(Property property) {
        long unitCount = property.getUnits().size();
        long occupiedUnitCount = occupiedUnitCount(property);
        long vacantUnitCount = property.getUnits().stream()
                .filter(unit -> unit.getStatus() == UnitStatus.VACANT)
                .count();
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getAddressLine1(),
                property.getAddressLine2(),
                property.getCity(),
                property.getProvince(),
                property.getPostalCode(),
                property.getCountry(),
                property.getOwnerUserId(),
                property.getNotes(),
                unitCount,
                occupiedUnitCount,
                vacantUnitCount,
                0,
                occupancyRate(unitCount, occupiedUnitCount),
                property.getCreatedAt(),
                property.getUpdatedAt(),
                property.getUnits().stream()
                        .map(this::toUnitResponse)
                        .toList());
    }

    public PropertyUnitResponse toUnitResponse(PropertyUnit unit) {
        return new PropertyUnitResponse(
                unit.getId(),
                unit.getProperty().getId(),
                unit.getUnitLabel(),
                unit.getStatus(),
                unit.getBedrooms(),
                unit.getBathrooms(),
                unit.getSquareFeet(),
                unit.getCurrentTenantNames(),
                unit.getCurrentRentCents(),
                unit.getCreatedAt(),
                unit.getUpdatedAt());
    }

    private long occupiedUnitCount(Property property) {
        return property.getUnits().stream()
                .filter(unit -> unit.getStatus() == UnitStatus.OCCUPIED || unit.getStatus() == UnitStatus.NOTICE_PENDING)
                .count();
    }

    private double occupancyRate(long unitCount, long occupiedUnitCount) {
        if (unitCount == 0) {
            return 0;
        }
        return Math.round(((double) occupiedUnitCount / unitCount) * 1000.0) / 10.0;
    }

    private String formattedAddress(Property property) {
        return String.join(", ",
                property.getAddressLine1(),
                property.getCity(),
                property.getProvince(),
                property.getPostalCode());
    }
}
