package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.EvidenceStrength;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvidencePackageResponse(
        UUID packageId,
        String packageVersion,
        String packageHash,
        UUID noticeId,
        UUID generatedByUserId,
        Instant generatedAt,
        NoticeResponse notice,
        List<EvidencePackageAttemptResponse> attempts,
        List<DeliveryEvidenceResponse> evidence,
        List<EvidenceDocumentResponse> evidenceDocuments,
        List<TrackingEventResponse> trackingHistory,
        List<AuditEventResponse> auditEvents,
        EvidenceStrength strongestEvidenceStrength) {
}
