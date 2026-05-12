package com.leasetrack.domain.entity;

import com.leasetrack.domain.enums.EvidenceDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_documents")
public class EvidenceDocument {

    @Id
    private UUID id;

    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_attempt_id", nullable = false)
    private DeliveryAttempt deliveryAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_evidence_id", nullable = false)
    private DeliveryEvidence deliveryEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private EvidenceDocumentType documentType;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_provider", nullable = false)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "sha256_checksum", nullable = false)
    private String sha256Checksum;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(UUID noticeId) {
        this.noticeId = noticeId;
    }

    public DeliveryAttempt getDeliveryAttempt() {
        return deliveryAttempt;
    }

    public void setDeliveryAttempt(DeliveryAttempt deliveryAttempt) {
        this.deliveryAttempt = deliveryAttempt;
    }

    public DeliveryEvidence getDeliveryEvidence() {
        return deliveryEvidence;
    }

    public void setDeliveryEvidence(DeliveryEvidence deliveryEvidence) {
        this.deliveryEvidence = deliveryEvidence;
    }

    public EvidenceDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(EvidenceDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public UUID getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(UUID uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
