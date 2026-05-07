package com.leasetrack.dto.request;

public record UpsertDeliveryEvidenceRequest(
        String trackingNumber,
        String carrierName,
        String carrierReceiptRef,
        Boolean deliveryConfirmation,
        String deliveryConfirmationMetadata,
        String signedAcknowledgementRef,
        String emailAcknowledgementRef,
        String emailAcknowledgementMetadata,
        String bailiffAffidavitRef) {
}
