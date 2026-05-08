package com.leasetrack.service;

import com.leasetrack.domain.entity.AuditEvent;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.EvidenceDocument;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.ActorRole;
import com.leasetrack.domain.enums.AuditEventType;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditService(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    public void recordNoticeCreated(Notice notice) {
        UUID attemptId = notice.getDeliveryAttempts().isEmpty()
                ? null
                : notice.getDeliveryAttempts().getFirst().getId();
        record(
                notice.getId(),
                attemptId,
                AuditEventType.NOTICE_CREATED,
                "{\"noticeType\":\"%s\",\"status\":\"%s\"}".formatted(notice.getNoticeType(), notice.getStatus()));
    }

    public void recordDeliveryStatusUpdated(
            DeliveryAttempt attempt,
            DeliveryAttemptStatus previousStatus,
            DeliveryAttemptStatus newStatus) {
        record(
                attempt.getNotice().getId(),
                attempt.getId(),
                AuditEventType.DELIVERY_STATUS_UPDATED,
                "{\"previousStatus\":\"%s\",\"newStatus\":\"%s\"}".formatted(previousStatus, newStatus));
    }

    public void recordEvidenceUpserted(DeliveryEvidence evidence, EvidenceStrength evidenceStrength, boolean created) {
        record(
                evidence.getDeliveryAttempt().getNotice().getId(),
                evidence.getDeliveryAttempt().getId(),
                created ? AuditEventType.EVIDENCE_ADDED : AuditEventType.EVIDENCE_UPDATED,
                "{\"evidenceId\":\"%s\",\"evidenceStrength\":\"%s\"}".formatted(evidence.getId(), evidenceStrength));
    }

    public void recordEvidenceDocumentUploaded(EvidenceDocument document) {
        record(
                document.getNoticeId(),
                document.getDeliveryAttempt().getId(),
                AuditEventType.EVIDENCE_DOCUMENT_UPLOADED,
                "{\"documentId\":\"%s\",\"documentType\":\"%s\",\"storageProvider\":\"%s\"}".formatted(
                        document.getId(),
                        document.getDocumentType(),
                        document.getStorageProvider()));
    }

    public void recordEvidencePackageGenerated(UUID noticeId) {
        record(
                noticeId,
                null,
                AuditEventType.EVIDENCE_PACKAGE_GENERATED,
                "{\"format\":\"JSON\"}");
    }

    public void recordDeadlineApproachingPublished(DeliveryAttempt attempt) {
        record(
                attempt.getNotice().getId(),
                attempt.getId(),
                AuditEventType.DEADLINE_APPROACHING_PUBLISHED,
                "{\"deadlineAt\":\"%s\"}".formatted(attempt.getDeadlineAt()));
    }

    public List<AuditEvent> getAuditEvents(UUID noticeId) {
        return auditEventRepository.findByNoticeIdOrderByCreatedAtAsc(noticeId);
    }

    private void record(UUID noticeId, UUID deliveryAttemptId, AuditEventType eventType, String details) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setId(UUID.randomUUID());
        auditEvent.setNoticeId(noticeId);
        auditEvent.setDeliveryAttemptId(deliveryAttemptId);
        auditEvent.setEventType(eventType);
        auditEvent.setActorRole(ActorRole.SYSTEM);
        auditEvent.setActorReference("system");
        auditEvent.setDetails(details);
        auditEvent.setCreatedAt(Instant.now(clock));
        auditEventRepository.save(auditEvent);
    }
}
