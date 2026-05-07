package com.leasetrack.mapper;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.dto.response.DeliveryAttemptResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
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

    private DeliveryAttemptResponse toResponse(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getDeliveryMethod(),
                attempt.getStatus(),
                attempt.getSentAt(),
                attempt.getDeliveredAt(),
                attempt.getDeadlineAt(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt());
    }

    public DeliveryEvidenceResponse toResponse(DeliveryEvidence evidence) {
        return new DeliveryEvidenceResponse(
                evidence.getId(),
                evidence.getDeliveryAttempt().getId(),
                evidence.getTrackingNumber(),
                evidence.getCarrierName(),
                evidence.getCarrierReceiptRef(),
                evidence.getDeliveryConfirmation(),
                evidence.getDeliveryConfirmationMetadata(),
                evidence.getSignedAcknowledgementRef(),
                evidence.getEmailAcknowledgementRef(),
                evidence.getEmailAcknowledgementMetadata(),
                evidence.getBailiffAffidavitRef(),
                evidence.getCreatedAt(),
                evidence.getUpdatedAt());
    }
}
