package com.leasetrack.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpsertDeliveryEvidenceRequest(
        @Schema(description = "Carrier tracking number. Required with carrierName unless trackingUrl is provided.")
        String trackingNumber,
        @Schema(description = "Carrier name or code, for example Canada Post, canada-post, FedEx, or fedex. Required with trackingNumber unless trackingUrl is provided.")
        String carrierName,
        @Schema(description = "Supported carrier tracking URL. Canada Post and FedEx URLs are parsed into carrierCode and trackingNumber.")
        String trackingUrl,
        String carrierReceiptRef,
        Boolean deliveryConfirmation,
        String deliveryConfirmationMetadata,
        String signedAcknowledgementRef,
        String emailAcknowledgementRef,
        String emailAcknowledgementMetadata,
        String bailiffAffidavitRef) {
}
