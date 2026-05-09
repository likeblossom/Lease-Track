package com.leasetrack.mapper;

import com.leasetrack.domain.entity.AuditEvent;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryTrackingEvent;
import com.leasetrack.domain.entity.EvidenceDocument;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.dto.response.AuditEventResponse;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.dto.response.DeliveryAttemptResponse;
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
import com.leasetrack.dto.response.TrackingEventResponse;
import org.springframework.stereotype.Component;

@Component
public class NoticeMapper {

    public NoticeResponse toResponse(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getRecipientName(),
                notice.getRecipientContactInfo(),
                notice.getNoticeType(),
                notice.getStatus(),
                notice.getOwnerUserId(),
                notice.getTenantUserId(),
                notice.getNotes(),
                notice.getCreatedAt(),
                notice.getUpdatedAt(),
                notice.getClosedAt(),
                notice.getDeliveryAttempts().stream()
                        .map(this::toResponse)
                        .toList());
    }

    public NoticeSummaryResponse toSummaryResponse(Notice notice) {
        return new NoticeSummaryResponse(
                notice.getId(),
                notice.getRecipientName(),
                notice.getNoticeType(),
                notice.getStatus(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }

    public DeliveryAttemptResponse toResponse(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getDeliveryMethod(),
                attempt.getStatus(),
                attempt.getSentAt(),
                attempt.getDeliveredAt(),
                attempt.getDeadlineAt(),
                attempt.getTrackingSyncStatus(),
                attempt.getLastTrackingCheckedAt(),
                attempt.isDeadlineReminderSent(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt());
    }

    public DeliveryEvidenceResponse toResponse(DeliveryEvidence evidence, EvidenceStrength evidenceStrength) {
        return new DeliveryEvidenceResponse(
                evidence.getId(),
                evidence.getDeliveryAttempt().getId(),
                evidence.getTrackingNumber(),
                evidence.getCarrierName(),
                evidence.getCarrierCode(),
                evidence.getCarrierReceiptRef(),
                evidence.getDeliveryConfirmation(),
                evidence.getDeliveryConfirmationMetadata(),
                evidence.getSignedAcknowledgementRef(),
                evidence.getEmailAcknowledgementRef(),
                evidence.getEmailAcknowledgementMetadata(),
                evidence.getBailiffAffidavitRef(),
                evidence.getLatestTrackingStatus(),
                evidence.getLatestTrackingStatusCode(),
                evidence.getLatestTrackingEventAt(),
                evidence.getLatestTrackingProviderError(),
                evidenceStrength,
                evidence.getCreatedAt(),
                evidence.getUpdatedAt());
    }

    public AuditEventResponse toResponse(AuditEvent auditEvent) {
        return new AuditEventResponse(
                auditEvent.getId(),
                auditEvent.getNoticeId(),
                auditEvent.getDeliveryAttemptId(),
                auditEvent.getEventType(),
                auditEvent.getActorRole(),
                auditEvent.getActorReference(),
                auditEvent.getDetails(),
                auditEvent.getCreatedAt());
    }

    public EvidenceDocumentResponse toResponse(EvidenceDocument document) {
        return new EvidenceDocumentResponse(
                document.getId(),
                document.getNoticeId(),
                document.getDeliveryAttempt().getId(),
                document.getDeliveryEvidence().getId(),
                document.getDocumentType(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStorageProvider(),
                document.getStorageKey(),
                document.getSha256Checksum(),
                document.getUploadedByUserId(),
                document.getCreatedAt());
    }

    public TrackingEventResponse toResponse(DeliveryTrackingEvent trackingEvent) {
        return new TrackingEventResponse(
                trackingEvent.getId(),
                trackingEvent.getDeliveryAttempt().getId(),
                trackingEvent.getTrackingNumber(),
                trackingEvent.getEventKey(),
                trackingEvent.getStatus(),
                trackingEvent.getStatusCode(),
                trackingEvent.isDelivered(),
                trackingEvent.getEventAt(),
                trackingEvent.getCheckedAt(),
                trackingEvent.getErrorMessage());
    }
}
