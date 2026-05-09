package com.leasetrack.dto.response;

import java.util.List;

public record EvidencePackageAttemptResponse(
        DeliveryAttemptResponse attempt,
        DeliveryEvidenceResponse evidence,
        List<EvidenceDocumentResponse> evidenceDocuments,
        List<TrackingEventResponse> trackingHistory) {
}
