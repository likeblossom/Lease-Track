package com.leasetrack.dto.response;

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
        Instant createdAt,
        Instant updatedAt) {
}
