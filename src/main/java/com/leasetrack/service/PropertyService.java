package com.leasetrack.service;

import com.leasetrack.domain.entity.Property;
import com.leasetrack.domain.entity.PropertyUnit;
import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.request.CreatePropertyRequest;
import com.leasetrack.dto.request.CreatePropertyUnitRequest;
import com.leasetrack.dto.response.PropertyResponse;
import com.leasetrack.dto.response.PropertySummaryResponse;
import com.leasetrack.dto.response.PropertyUnitResponse;
import com.leasetrack.mapper.PropertyMapper;
import com.leasetrack.repository.PropertyRepository;
import com.leasetrack.repository.PropertyUnitRepository;
import com.leasetrack.repository.specification.PropertySpecifications;
import com.leasetrack.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyUnitRepository propertyUnitRepository;
    private final PropertyMapper propertyMapper;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public PropertyService(
            PropertyRepository propertyRepository,
            PropertyUnitRepository propertyUnitRepository,
            PropertyMapper propertyMapper,
            CurrentUserService currentUserService,
            Clock clock) {
        this.propertyRepository = propertyRepository;
        this.propertyUnitRepository = propertyUnitRepository;
        this.propertyMapper = propertyMapper;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional
    public PropertyResponse createProperty(CreatePropertyRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanManageProperties(currentUser);

        Instant now = Instant.now(clock);
        Property property = new Property();
        property.setId(UUID.randomUUID());
        property.setName(request.name());
        property.setAddressLine1(request.addressLine1());
        property.setAddressLine2(blankToNull(request.addressLine2()));
        property.setCity(request.city());
        property.setProvince(request.province());
        property.setPostalCode(request.postalCode());
        property.setCountry(normalizeCountry(request.country()));
        property.setOwnerUserId(currentUser.getId());
        property.setNotes(blankToNull(request.notes()));
        property.setCreatedAt(now);
        property.setUpdatedAt(now);
        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    @Transactional
    public PropertyUnitResponse createUnit(UUID propertyId, CreatePropertyUnitRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanManageProperties(currentUser);
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        assertCanRead(currentUser, property);

        Instant now = Instant.now(clock);
        PropertyUnit unit = new PropertyUnit();
        unit.setId(UUID.randomUUID());
        unit.setProperty(property);
        unit.setUnitLabel(request.unitLabel());
        unit.setStatus(request.status());
        unit.setBedrooms(request.bedrooms());
        unit.setBathrooms(request.bathrooms());
        unit.setSquareFeet(request.squareFeet());
        unit.setCurrentTenantNames(blankToNull(request.currentTenantNames()));
        unit.setCurrentRentCents(request.currentRentCents());
        unit.setCreatedAt(now);
        unit.setUpdatedAt(now);

        property.setUpdatedAt(now);
        propertyRepository.save(property);
        return propertyMapper.toUnitResponse(propertyUnitRepository.save(unit));
    }

    @Transactional(readOnly = true)
    public PropertyResponse getProperty(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        assertCanRead(currentUserService.currentUser(), property);
        return propertyMapper.toResponse(property);
    }

    @Transactional(readOnly = true)
    public Page<PropertySummaryResponse> listProperties(String query, Pageable pageable) {
        User currentUser = currentUserService.currentUser();
        Specification<Property> specification = PropertySpecifications
                .accessibleTo(currentUser.getId(), currentUser.getRole())
                .and(PropertySpecifications.search(query));
        return propertyRepository.findAll(specification, pageable)
                .map(propertyMapper::toSummaryResponse);
    }

    private void assertCanManageProperties(User user) {
        if (user.getRole() == UserRole.TENANT) {
            throw new AccessDeniedException("Tenant users cannot manage properties");
        }
    }

    private void assertCanRead(User user, Property property) {
        if (user.getRole() == UserRole.ADMIN || user.getId().equals(property.getOwnerUserId())) {
            return;
        }
        throw new AccessDeniedException("User cannot access this property");
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "Canada";
        }
        return country.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
