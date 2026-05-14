package com.leasetrack.domain.entity;

import com.leasetrack.domain.enums.LeaseStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "leases")
public class Lease {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "property_address", nullable = false)
    private String propertyAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private PropertyUnit unit;

    @Column(name = "tenant_names", nullable = false, columnDefinition = "text")
    private String tenantNames;

    @Column(name = "tenant_email")
    private String tenantEmail;

    @Column(name = "tenant_phone")
    private String tenantPhone;

    @Column(name = "lease_start_date", nullable = false)
    private LocalDate leaseStartDate;

    @Column(name = "lease_end_date", nullable = false)
    private LocalDate leaseEndDate;

    @Column(name = "rent_cents", nullable = false)
    private Long rentCents;

    @Column(name = "security_deposit_cents")
    private Long securityDepositCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseStatus status;

    @Column(name = "renewal_decision_due_date")
    private LocalDate renewalDecisionDueDate;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "lease", cascade = CascadeType.ALL)
    @OrderBy("createdAt DESC")
    private List<Notice> notices = new ArrayList<>();

    @OneToMany(mappedBy = "lease", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<LeaseEvent> events = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPropertyAddress() {
        return propertyAddress;
    }

    public void setPropertyAddress(String propertyAddress) {
        this.propertyAddress = propertyAddress;
    }

    public PropertyUnit getUnit() {
        return unit;
    }

    public void setUnit(PropertyUnit unit) {
        this.unit = unit;
    }

    public String getTenantNames() {
        return tenantNames;
    }

    public void setTenantNames(String tenantNames) {
        this.tenantNames = tenantNames;
    }

    public String getTenantEmail() {
        return tenantEmail;
    }

    public void setTenantEmail(String tenantEmail) {
        this.tenantEmail = tenantEmail;
    }

    public String getTenantPhone() {
        return tenantPhone;
    }

    public void setTenantPhone(String tenantPhone) {
        this.tenantPhone = tenantPhone;
    }

    public LocalDate getLeaseStartDate() {
        return leaseStartDate;
    }

    public void setLeaseStartDate(LocalDate leaseStartDate) {
        this.leaseStartDate = leaseStartDate;
    }

    public LocalDate getLeaseEndDate() {
        return leaseEndDate;
    }

    public void setLeaseEndDate(LocalDate leaseEndDate) {
        this.leaseEndDate = leaseEndDate;
    }

    public Long getRentCents() {
        return rentCents;
    }

    public void setRentCents(Long rentCents) {
        this.rentCents = rentCents;
    }

    public Long getSecurityDepositCents() {
        return securityDepositCents;
    }

    public void setSecurityDepositCents(Long securityDepositCents) {
        this.securityDepositCents = securityDepositCents;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public void setStatus(LeaseStatus status) {
        this.status = status;
    }

    public LocalDate getRenewalDecisionDueDate() {
        return renewalDecisionDueDate;
    }

    public void setRenewalDecisionDueDate(LocalDate renewalDecisionDueDate) {
        this.renewalDecisionDueDate = renewalDecisionDueDate;
    }

    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(Instant terminatedAt) {
        this.terminatedAt = terminatedAt;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Notice> getNotices() {
        return notices;
    }

    public void setNotices(List<Notice> notices) {
        this.notices = notices;
    }

    public List<LeaseEvent> getEvents() {
        return events;
    }

    public void setEvents(List<LeaseEvent> events) {
        this.events = events;
    }
}
