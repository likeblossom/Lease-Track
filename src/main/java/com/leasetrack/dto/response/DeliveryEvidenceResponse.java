package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.EvidenceStrength;
import java.time.Instant;
import java.util.UUID;

public record DeliveryEvidenceResponse(
        UUID id,
        UUID deliveryAttemptId,
        String trackingNumber,
        String carrierName,
        String carrierCode,
        String carrierReceiptRef,
        Boolean deliveryConfirmation,
        String deliveryConfirmationMetadata,
        String signedAcknowledgementRef,
        String emailAcknowledgementRef,
        String emailAcknowledgementMetadata,
        String bailiffAffidavitRef,
        String latestTrackingStatus,
        String latestTrackingStatusCode,
        Instant latestTrackingEventAt,
        String latestTrackingProviderError,
        EvidenceStrength evidenceStrength,
        Instant createdAt,
        Instant updatedAt) {
}
