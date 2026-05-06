package com.leasetrack.domain.entity;

import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notices")
public class Notice {

    @Id
    private UUID id;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_contact_info", nullable = false)
    private String recipientContactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false)
    private NoticeType noticeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NoticeStatus status;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "tenant_user_id")
    private UUID tenantUserId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptNumber ASC")
    private List<DeliveryAttempt> deliveryAttempts = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientContactInfo() {
        return recipientContactInfo;
    }

    public void setRecipientContactInfo(String recipientContactInfo) {
        this.recipientContactInfo = recipientContactInfo;
    }

    public NoticeType getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(NoticeType noticeType) {
        this.noticeType = noticeType;
    }

    public NoticeStatus getStatus() {
        return status;
    }

    public void setStatus(NoticeStatus status) {
        this.status = status;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getTenantUserId() {
        return tenantUserId;
    }

    public void setTenantUserId(UUID tenantUserId) {
        this.tenantUserId = tenantUserId;
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

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public List<DeliveryAttempt> getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(List<DeliveryAttempt> deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }
}
