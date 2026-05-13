package com.leasetrack.repository;

import com.leasetrack.domain.entity.PropertyUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyUnitRepository extends JpaRepository<PropertyUnit, UUID> {

    List<PropertyUnit> findByPropertyIdOrderByUnitLabelAsc(UUID propertyId);
}
