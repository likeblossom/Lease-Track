package com.leasetrack.service;

import com.leasetrack.domain.entity.Lease;
import com.leasetrack.domain.entity.LeaseEvent;
import com.leasetrack.domain.entity.PropertyUnit;
import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.ActorRole;
import com.leasetrack.domain.enums.LeaseEventType;
import com.leasetrack.domain.enums.LeaseStatus;
import com.leasetrack.domain.enums.UnitStatus;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.request.CreateLeaseRequest;
import com.leasetrack.dto.request.RenewLeaseRequest;
import com.leasetrack.dto.request.TerminateLeaseRequest;
import com.leasetrack.dto.request.UpdateLeaseRequest;
import com.leasetrack.dto.response.LeaseEventResponse;
import com.leasetrack.dto.response.LeaseResponse;
import com.leasetrack.dto.response.LeaseSummaryResponse;
import com.leasetrack.mapper.LeaseMapper;
import com.leasetrack.repository.LeaseEventRepository;
import com.leasetrack.repository.LeaseRepository;
import com.leasetrack.repository.PropertyUnitRepository;
import com.leasetrack.repository.specification.LeaseSpecifications;
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
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final LeaseEventRepository leaseEventRepository;
    private final PropertyUnitRepository propertyUnitRepository;
    private final LeaseMapper leaseMapper;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public LeaseService(
            LeaseRepository leaseRepository,
            LeaseEventRepository leaseEventRepository,
            PropertyUnitRepository propertyUnitRepository,
            LeaseMapper leaseMapper,
            CurrentUserService currentUserService,
            Clock clock) {
        this.leaseRepository = leaseRepository;
        this.leaseEventRepository = leaseEventRepository;
        this.propertyUnitRepository = propertyUnitRepository;
        this.leaseMapper = leaseMapper;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional
    public LeaseResponse createLease(CreateLeaseRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanManageLeases(currentUser);
        if (request.leaseEndDate().isBefore(request.leaseStartDate())) {
            throw new IllegalArgumentException("Lease end date cannot be before lease start date");
        }

        Instant now = Instant.now(clock);
        Lease lease = new Lease();
        lease.setId(UUID.randomUUID());
        lease.setName(request.name());
        lease.setPropertyAddress(request.propertyAddress());
        lease.setUnit(resolveUnit(request.unitId(), currentUser));
        lease.setTenantNames(request.tenantNames());
        lease.setTenantEmail(request.tenantEmail());
        lease.setTenantPhone(request.tenantPhone());
        lease.setLeaseStartDate(request.leaseStartDate());
        lease.setLeaseEndDate(request.leaseEndDate());
        lease.setRentCents(request.rentCents());
        lease.setSecurityDepositCents(request.securityDepositCents());
        lease.setStatus(initialStatus(request.leaseEndDate()));
        lease.setRenewalDecisionDueDate(request.renewalDecisionDueDate());
        lease.setOwnerUserId(currentUser.getId());
        lease.setNotes(request.notes());
        lease.setCreatedAt(now);
        lease.setUpdatedAt(now);
        Lease savedLease = leaseRepository.save(lease);
        syncUnitOccupancy(savedLease);
        recordEvent(
                savedLease,
                currentUser,
                LeaseEventType.LEASE_CREATED,
                "{\"status\":\"%s\",\"rentCents\":%d}".formatted(savedLease.getStatus(), savedLease.getRentCents()));
        return leaseMapper.toResponse(savedLease);
    }

    @Transactional(readOnly = true)
    public LeaseResponse getLease(UUID leaseId) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found"));
        assertCanRead(currentUserService.currentUser(), lease);
        return leaseMapper.toResponse(lease);
    }

    @Transactional(readOnly = true)
    public Page<LeaseSummaryResponse> listLeases(String query, LeaseStatus status, UUID unitId, Pageable pageable) {
        User currentUser = currentUserService.currentUser();
        Specification<Lease> specification = LeaseSpecifications.accessibleTo(currentUser.getId(), currentUser.getRole())
                .and(LeaseSpecifications.search(query))
                .and(LeaseSpecifications.hasStatus(status))
                .and(LeaseSpecifications.belongsToUnit(unitId));
        return leaseRepository.findAll(specification, pageable)
                .map(leaseMapper::toSummaryResponse);
    }

    @Transactional
    public LeaseResponse updateLease(UUID leaseId, UpdateLeaseRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanManageLeases(currentUser);
        Lease lease = getAccessibleLease(leaseId, currentUser);
        if (request.leaseEndDate().isBefore(request.leaseStartDate())) {
            throw new IllegalArgumentException("Lease end date cannot be before lease start date");
        }

        LeaseStatus previousStatus = lease.getStatus();
        lease.setName(request.name());
        lease.setPropertyAddress(request.propertyAddress());
        lease.setUnit(resolveUnit(request.unitId(), currentUser));
        lease.setTenantNames(request.tenantNames());
        lease.setTenantEmail(blankToNull(request.tenantEmail()));
        lease.setTenantPhone(blankToNull(request.tenantPhone()));
        lease.setLeaseStartDate(request.leaseStartDate());
        lease.setLeaseEndDate(request.leaseEndDate());
        lease.setRentCents(request.rentCents());
        lease.setSecurityDepositCents(request.securityDepositCents());
        lease.setStatus(request.status() == null ? initialStatus(request.leaseEndDate()) : persistedStatus(request.status()));
        lease.setRenewalDecisionDueDate(request.renewalDecisionDueDate());
        lease.setNotes(blankToNull(request.notes()));
        lease.setUpdatedAt(Instant.now(clock));
        syncUnitOccupancy(lease);

        if (previousStatus != lease.getStatus()) {
            recordEvent(
                    lease,
                    currentUser,
                    LeaseEventType.STATUS_CHANGED,
                    "{\"previousStatus\":\"%s\",\"newStatus\":\"%s\"}".formatted(previousStatus, lease.getStatus()));
        }
        recordEvent(
                lease,
                currentUser,
                LeaseEventType.LEASE_UPDATED,
                "{\"rentCents\":%d,\"leaseEndDate\":\"%s\"}".formatted(lease.getRentCents(), lease.getLeaseEndDate()));
        return leaseMapper.toResponse(leaseRepository.save(lease));
    }

    @Transactional
    public LeaseResponse requestRenewal(UUID leaseId) {
        User currentUser = currentUserService.currentUser();
        assertCanManageLeases(currentUser);
        Lease lease = getAccessibleLease(leaseId, currentUser);
        LeaseStatus previousStatus = lease.getStatus();
        lease.setStatus(LeaseStatus.PENDING_RENEWAL);
        lease.setUpdatedAt(Instant.now(clock));
        recordEvent(
                lease,
                currentUser,
                LeaseEventType.RENEWAL_REQUESTED,
                "{\"previousStatus\":\"%s\",\"newStatus\":\"%s\"}".formatted(previousStatus, lease.getStatus()));
        return leaseMapper.toResponse(leaseRepository.save(lease));
    }

    @Transactional
    public LeaseResponse renewLease(UUID leaseId, RenewLeaseRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanManageLeases(currentUser);
        Lease lease = getAccessibleLease(leaseId, currentUser);
        if (request.nextEndDate().isBefore(request.nextStartDate())) {
            throw new IllegalArgumentException("Renewal end date cannot be before renewal start date");
        }

        lease.setLeaseStartDate(request.nextStartDate());
        lease.setLeaseEndDate(request.nextEndDate());
        lease.setRentCents(request.rentCents());
        lease.setRenewalDecisionDueDate(request.renewalDecisionDueDate());
        lease.setStatus(initialStatus(request.nextEndDate()));
        lease.setTerminatedAt(null);
        lease.setUpdatedAt(Instant.now(clock));
        syncUnitOccupancy(lease);
        recordEvent(
                lease,
                currentUser,
                LeaseEventType.RENEWAL_COMPLETED,
                "{\"nextStartDate\":\"%s\",\"nextEndDate\":\"%s\",\"rentCents\":%d,\"notes\":\"%s\"}".formatted(
                        request.nextStartDate(),
                        request.nextEndDate(),
                        request.rentCents(),
                        escapeJson(blankToNull(request.notes()))));
        return leaseMapper.toResponse(leaseRepository.save(lease));
    }

    @Transactional
    public LeaseResponse terminateLease(UUID leaseId, TerminateLeaseRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanManageLeases(currentUser);
        Lease lease = getAccessibleLease(leaseId, currentUser);
        Instant now = Instant.now(clock);
        LeaseStatus previousStatus = lease.getStatus();
        lease.setStatus(LeaseStatus.TERMINATED);
        lease.setTerminatedAt(now);
        lease.setUpdatedAt(now);
        recordEvent(
                lease,
                currentUser,
                LeaseEventType.LEASE_TERMINATED,
                "{\"previousStatus\":\"%s\",\"reason\":\"%s\"}".formatted(
                        previousStatus,
                        escapeJson(blankToNull(request.reason()))));
        return leaseMapper.toResponse(leaseRepository.save(lease));
    }

    @Transactional(readOnly = true)
    public java.util.List<LeaseEventResponse> getLeaseTimeline(UUID leaseId) {
        User currentUser = currentUserService.currentUser();
        Lease lease = getAccessibleLease(leaseId, currentUser);
        return leaseEventRepository.findByLeaseIdOrderByCreatedAtAsc(lease.getId()).stream()
                .map(leaseMapper::toEventResponse)
                .toList();
    }

    private Lease getAccessibleLease(UUID leaseId, User currentUser) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found"));
        assertCanRead(currentUser, lease);
        return lease;
    }

    private void assertCanManageLeases(User user) {
        if (user.getRole() == UserRole.TENANT) {
            throw new AccessDeniedException("Tenant users cannot manage leases");
        }
    }

    private void assertCanRead(User user, Lease lease) {
        if (user.getRole() == UserRole.ADMIN || user.getId().equals(lease.getOwnerUserId())) {
            return;
        }
        throw new AccessDeniedException("User cannot access this lease");
    }

    private PropertyUnit resolveUnit(UUID unitId, User currentUser) {
        if (unitId == null) {
            return null;
        }
        PropertyUnit unit = propertyUnitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found"));
        if (currentUser.getRole() != UserRole.ADMIN
                && !currentUser.getId().equals(unit.getProperty().getOwnerUserId())) {
            throw new AccessDeniedException("User cannot access this unit");
        }
        return unit;
    }

    private void syncUnitOccupancy(Lease lease) {
        PropertyUnit unit = lease.getUnit();
        if (unit == null || lease.getStatus() == LeaseStatus.TERMINATED) {
            return;
        }
        unit.setStatus(UnitStatus.OCCUPIED);
        unit.setCurrentTenantNames(lease.getTenantNames());
        unit.setCurrentRentCents(lease.getRentCents());
        unit.setUpdatedAt(lease.getUpdatedAt());
        propertyUnitRepository.save(unit);
    }

    private LeaseStatus initialStatus(java.time.LocalDate leaseEndDate) {
        if (leaseEndDate.isBefore(java.time.LocalDate.now(clock))) {
            return LeaseStatus.EXPIRED;
        }
        return LeaseStatus.ACTIVE;
    }

    private LeaseStatus persistedStatus(LeaseStatus status) {
        if (status == LeaseStatus.EXPIRING_SOON) {
            return LeaseStatus.ACTIVE;
        }
        return status;
    }

    private void recordEvent(Lease lease, User actor, LeaseEventType eventType, String details) {
        LeaseEvent event = new LeaseEvent();
        event.setId(UUID.randomUUID());
        event.setLease(lease);
        event.setEventType(eventType);
        event.setActorRole(actorRole(actor));
        event.setActorReference(actor.getEmail());
        event.setDetails(details);
        event.setCreatedAt(Instant.now(clock));
        LeaseEvent savedEvent = leaseEventRepository.save(event);
        lease.getEvents().add(savedEvent);
    }

    private ActorRole actorRole(User user) {
        return switch (user.getRole()) {
            case ADMIN -> ActorRole.ADMIN;
            case LANDLORD -> ActorRole.LANDLORD;
            case PROPERTY_MANAGER -> ActorRole.PROPERTY_MANAGER;
            case TENANT -> ActorRole.TENANT;
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
