package com.leasetrack.controller;

import com.leasetrack.dto.request.CreatePropertyRequest;
import com.leasetrack.dto.request.CreatePropertyUnitRequest;
import com.leasetrack.dto.response.PropertyResponse;
import com.leasetrack.dto.response.PropertySummaryResponse;
import com.leasetrack.dto.response.PropertyUnitResponse;
import com.leasetrack.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/properties")
@Tag(name = "Properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @Operation(summary = "Create a property")
    public ResponseEntity<PropertyResponse> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        PropertyResponse response = propertyService.createProperty(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a property, units, and occupancy metrics")
    public PropertyResponse getProperty(@PathVariable UUID id) {
        return propertyService.getProperty(id);
    }

    @GetMapping
    @Operation(summary = "List properties visible to the authenticated user")
    public Page<PropertySummaryResponse> listProperties(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return propertyService.listProperties(q, pageable);
    }

    @PostMapping("/{id}/units")
    @Operation(summary = "Create a unit within a property")
    public ResponseEntity<PropertyUnitResponse> createUnit(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePropertyUnitRequest request) {
        PropertyUnitResponse response = propertyService.createUnit(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{unitId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
}
