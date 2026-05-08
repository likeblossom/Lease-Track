package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.EvidenceStrength;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvidencePackageResponse(
        UUID noticeId,
        Instant generatedAt,
        NoticeResponse notice,
        List<DeliveryEvidenceResponse> evidence,
        List<EvidenceDocumentResponse> evidenceDocuments,
        List<AuditEventResponse> auditEvents,
        EvidenceStrength strongestEvidenceStrength) {
}
