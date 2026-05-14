package com.leasetrack.controller;

import com.leasetrack.domain.enums.LeaseStatus;
import com.leasetrack.dto.request.CreateLeaseRequest;
import com.leasetrack.dto.request.RenewLeaseRequest;
import com.leasetrack.dto.request.TerminateLeaseRequest;
import com.leasetrack.dto.request.UpdateLeaseRequest;
import com.leasetrack.dto.response.LeaseEventResponse;
import com.leasetrack.dto.response.LeaseResponse;
import com.leasetrack.dto.response.LeaseSummaryResponse;
import com.leasetrack.service.LeaseService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/leases")
@Tag(name = "Leases")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    @Operation(summary = "Create a lease")
    public ResponseEntity<LeaseResponse> createLease(@Valid @RequestBody CreateLeaseRequest request) {
        LeaseResponse response = leaseService.createLease(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a lease and its notices")
    public LeaseResponse getLease(@PathVariable UUID id) {
        return leaseService.getLease(id);
    }

    @GetMapping
    @Operation(summary = "List leases visible to the authenticated user")
    public Page<LeaseSummaryResponse> listLeases(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LeaseStatus status,
            @RequestParam(required = false) UUID unitId,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return leaseService.listLeases(q, status, unitId, pageable);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a lease record")
    public LeaseResponse updateLease(@PathVariable UUID id, @Valid @RequestBody UpdateLeaseRequest request) {
        return leaseService.updateLease(id, request);
    }

    @PostMapping("/{id}/renewal")
    @Operation(summary = "Mark a lease as pending renewal")
    public LeaseResponse requestRenewal(@PathVariable UUID id) {
        return leaseService.requestRenewal(id);
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Complete a lease renewal")
    public LeaseResponse renewLease(@PathVariable UUID id, @Valid @RequestBody RenewLeaseRequest request) {
        return leaseService.renewLease(id, request);
    }

    @PostMapping("/{id}/terminate")
    @Operation(summary = "Terminate a lease")
    public LeaseResponse terminateLease(@PathVariable UUID id, @RequestBody TerminateLeaseRequest request) {
        return leaseService.terminateLease(id, request);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Retrieve immutable lease lifecycle history")
    public java.util.List<LeaseEventResponse> getLeaseTimeline(@PathVariable UUID id) {
        return leaseService.getLeaseTimeline(id);
    }
}
