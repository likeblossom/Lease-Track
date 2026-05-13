package com.leasetrack.service;

import com.leasetrack.domain.entity.Lease;
import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.request.CreateLeaseRequest;
import com.leasetrack.dto.response.LeaseResponse;
import com.leasetrack.dto.response.LeaseSummaryResponse;
import com.leasetrack.mapper.LeaseMapper;
import com.leasetrack.repository.LeaseRepository;
import com.leasetrack.repository.specification.LeaseSpecifications;
import com.leasetrack.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final LeaseMapper leaseMapper;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public LeaseService(
            LeaseRepository leaseRepository,
            LeaseMapper leaseMapper,
            CurrentUserService currentUserService,
            Clock clock) {
        this.leaseRepository = leaseRepository;
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
        lease.setTenantNames(request.tenantNames());
        lease.setTenantEmail(request.tenantEmail());
        lease.setTenantPhone(request.tenantPhone());
        lease.setLeaseStartDate(request.leaseStartDate());
        lease.setLeaseEndDate(request.leaseEndDate());
        lease.setOwnerUserId(currentUser.getId());
        lease.setNotes(request.notes());
        lease.setCreatedAt(now);
        lease.setUpdatedAt(now);
        return leaseMapper.toResponse(leaseRepository.save(lease));
    }

    @Transactional(readOnly = true)
    public LeaseResponse getLease(UUID leaseId) {
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found"));
        assertCanRead(currentUserService.currentUser(), lease);
        return leaseMapper.toResponse(lease);
    }

    @Transactional(readOnly = true)
    public Page<LeaseSummaryResponse> listLeases(Pageable pageable) {
        User currentUser = currentUserService.currentUser();
        return leaseRepository.findAll(
                        LeaseSpecifications.accessibleTo(currentUser.getId(), currentUser.getRole()),
                        pageable)
                .map(leaseMapper::toSummaryResponse);
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
}
