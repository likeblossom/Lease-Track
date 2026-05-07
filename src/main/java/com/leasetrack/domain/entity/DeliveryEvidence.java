package com.leasetrack.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_evidence")
public class DeliveryEvidence {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_attempt_id", nullable = false, unique = true)
    private DeliveryAttempt deliveryAttempt;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "carrier_receipt_ref")
    private String carrierReceiptRef;

    @Column(name = "delivery_confirmation")
    private Boolean deliveryConfirmation;

    @Column(name = "delivery_confirmation_metadata", columnDefinition = "text")
    private String deliveryConfirmationMetadata;

    @Column(name = "signed_acknowledgement_ref")
    private String signedAcknowledgementRef;

    @Column(name = "email_acknowledgement_ref")
    private String emailAcknowledgementRef;

    @Column(name = "email_acknowledgement_metadata", columnDefinition = "text")
    private String emailAcknowledgementMetadata;

    @Column(name = "bailiff_affidavit_ref")
    private String bailiffAffidavitRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DeliveryAttempt getDeliveryAttempt() {
        return deliveryAttempt;
    }

    public void setDeliveryAttempt(DeliveryAttempt deliveryAttempt) {
        this.deliveryAttempt = deliveryAttempt;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getCarrierReceiptRef() {
        return carrierReceiptRef;
    }

    public void setCarrierReceiptRef(String carrierReceiptRef) {
        this.carrierReceiptRef = carrierReceiptRef;
    }

    public Boolean getDeliveryConfirmation() {
        return deliveryConfirmation;
    }

    public void setDeliveryConfirmation(Boolean deliveryConfirmation) {
        this.deliveryConfirmation = deliveryConfirmation;
    }

    public String getDeliveryConfirmationMetadata() {
        return deliveryConfirmationMetadata;
    }

    public void setDeliveryConfirmationMetadata(String deliveryConfirmationMetadata) {
        this.deliveryConfirmationMetadata = deliveryConfirmationMetadata;
    }

    public String getSignedAcknowledgementRef() {
        return signedAcknowledgementRef;
    }

    public void setSignedAcknowledgementRef(String signedAcknowledgementRef) {
        this.signedAcknowledgementRef = signedAcknowledgementRef;
    }

    public String getEmailAcknowledgementRef() {
        return emailAcknowledgementRef;
    }

    public void setEmailAcknowledgementRef(String emailAcknowledgementRef) {
        this.emailAcknowledgementRef = emailAcknowledgementRef;
    }

    public String getEmailAcknowledgementMetadata() {
        return emailAcknowledgementMetadata;
    }

    public void setEmailAcknowledgementMetadata(String emailAcknowledgementMetadata) {
        this.emailAcknowledgementMetadata = emailAcknowledgementMetadata;
    }

    public String getBailiffAffidavitRef() {
        return bailiffAffidavitRef;
    }

    public void setBailiffAffidavitRef(String bailiffAffidavitRef) {
        this.bailiffAffidavitRef = bailiffAffidavitRef;
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
}
