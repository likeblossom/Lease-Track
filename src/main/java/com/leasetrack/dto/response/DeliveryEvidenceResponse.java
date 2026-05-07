package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.EvidenceStrength;
import java.time.Instant;
import java.util.UUID;

public record DeliveryEvidenceResponse(
        UUID id,
        UUID deliveryAttemptId,
        String trackingNumber,
        String carrierName,
        String carrierReceiptRef,
        Boolean deliveryConfirmation,
        String deliveryConfirmationMetadata,
        String signedAcknowledgementRef,
        String emailAcknowledgementRef,
        String emailAcknowledgementMetadata,
        String bailiffAffidavitRef,
        EvidenceStrength evidenceStrength,
        Instant createdAt,
        Instant updatedAt) {
}
