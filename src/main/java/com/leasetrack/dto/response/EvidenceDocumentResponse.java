package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.EvidenceDocumentType;
import java.time.Instant;
import java.util.UUID;

public record EvidenceDocumentResponse(
        UUID id,
        UUID noticeId,
        UUID deliveryAttemptId,
        UUID deliveryEvidenceId,
        EvidenceDocumentType documentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String storageProvider,
        String storageKey,
        String sha256Checksum,
        UUID uploadedByUserId,
        Instant createdAt) {
}
