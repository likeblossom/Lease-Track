package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.dto.request.CreateNoticeRequest;
import com.leasetrack.dto.request.UpdateDeliveryAttemptStatusRequest;
import com.leasetrack.dto.request.UpsertDeliveryEvidenceRequest;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
import com.leasetrack.exception.DeliveryAttemptNotFoundException;
import com.leasetrack.exception.InvalidStatusTransitionException;
import com.leasetrack.exception.NoticeNotFoundException;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryEvidenceRepository;
import com.leasetrack.repository.NoticeRepository;
import com.leasetrack.repository.specification.NoticeSpecifications;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryEvidenceRepository deliveryEvidenceRepository;
    private final NoticeMapper noticeMapper;
    private final Clock clock;

    public NoticeService(
            NoticeRepository noticeRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEvidenceRepository deliveryEvidenceRepository,
            NoticeMapper noticeMapper,
            Clock clock) {
        this.noticeRepository = noticeRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEvidenceRepository = deliveryEvidenceRepository;
        this.noticeMapper = noticeMapper;
        this.clock = clock;
    }

    @Transactional
    public NoticeResponse createNotice(CreateNoticeRequest request) {
        Instant now = Instant.now(clock);

        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setRecipientName(request.recipientName());
        notice.setRecipientContactInfo(request.recipientContactInfo());
        notice.setNoticeType(request.noticeType());
        notice.setStatus(NoticeStatus.OPEN);
        notice.setOwnerUserId(request.ownerUserId());
        notice.setTenantUserId(request.tenantUserId());
        notice.setNotes(request.notes());
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);
        attempt.setAttemptNumber(1);
        attempt.setDeliveryMethod(request.deliveryMethod());
        attempt.setStatus(DeliveryAttemptStatus.PENDING);
        attempt.setDeadlineAt(request.deadlineAt());
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        notice.getDeliveryAttempts().add(attempt);

        return noticeMapper.toResponse(noticeRepository.save(notice));
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        return noticeMapper.toResponse(notice);
    }

    @Transactional(readOnly = true)
    public Page<NoticeSummaryResponse> listNotices(
            NoticeStatus status,
            NoticeType noticeType,
            DeliveryMethod deliveryMethod,
            Instant deadlineAfter,
            Instant deadlineBefore,
            Pageable pageable) {
        Specification<Notice> specification = Specification.allOf(
                NoticeSpecifications.hasStatus(status),
                NoticeSpecifications.hasNoticeType(noticeType),
                NoticeSpecifications.hasDeliveryMethod(deliveryMethod),
                NoticeSpecifications.hasDeadlineOnOrAfter(deadlineAfter),
                NoticeSpecifications.hasDeadlineOnOrBefore(deadlineBefore));

        return noticeRepository.findAll(specification, pageable)
                .map(noticeMapper::toSummaryResponse);
    }

    @Transactional
    public NoticeResponse updateDeliveryAttemptStatus(
            UUID noticeId,
            UUID deliveryAttemptId,
            UpdateDeliveryAttemptStatusRequest request) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findByIdAndNotice_Id(deliveryAttemptId, noticeId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(noticeId, deliveryAttemptId));

        DeliveryAttemptStatus targetStatus = request.status();
        validateTransition(attempt.getStatus(), targetStatus);

        Instant now = Instant.now(clock);
        attempt.setStatus(targetStatus);
        attempt.setUpdatedAt(now);

        if (targetStatus == DeliveryAttemptStatus.SENT && attempt.getSentAt() == null) {
            attempt.setSentAt(now);
        }
        if (targetStatus == DeliveryAttemptStatus.DELIVERED) {
            attempt.setDeliveredAt(now);
        }

        return noticeMapper.toResponse(attempt.getNotice());
    }

    @Transactional
    public DeliveryEvidenceResponse upsertDeliveryEvidence(
            UUID noticeId,
            UUID deliveryAttemptId,
            UpsertDeliveryEvidenceRequest request) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findByIdAndNotice_Id(deliveryAttemptId, noticeId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(noticeId, deliveryAttemptId));

        Instant now = Instant.now(clock);
        DeliveryEvidence evidence = deliveryEvidenceRepository.findByDeliveryAttempt_Id(deliveryAttemptId)
                .orElseGet(() -> {
                    DeliveryEvidence newEvidence = new DeliveryEvidence();
                    newEvidence.setId(UUID.randomUUID());
                    newEvidence.setDeliveryAttempt(attempt);
                    newEvidence.setCreatedAt(now);
                    return newEvidence;
                });

        evidence.setTrackingNumber(request.trackingNumber());
        evidence.setCarrierName(request.carrierName());
        evidence.setCarrierReceiptRef(request.carrierReceiptRef());
        evidence.setDeliveryConfirmation(request.deliveryConfirmation());
        evidence.setDeliveryConfirmationMetadata(request.deliveryConfirmationMetadata());
        evidence.setSignedAcknowledgementRef(request.signedAcknowledgementRef());
        evidence.setEmailAcknowledgementRef(request.emailAcknowledgementRef());
        evidence.setEmailAcknowledgementMetadata(request.emailAcknowledgementMetadata());
        evidence.setBailiffAffidavitRef(request.bailiffAffidavitRef());
        evidence.setUpdatedAt(now);
        attempt.setEvidence(evidence);

        return noticeMapper.toResponse(deliveryEvidenceRepository.save(evidence));
    }

    private void validateTransition(DeliveryAttemptStatus currentStatus, DeliveryAttemptStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }

        boolean allowed = switch (currentStatus) {
            case PENDING -> EnumSet.of(DeliveryAttemptStatus.SENT, DeliveryAttemptStatus.CANCELLED)
                    .contains(targetStatus);
            case SENT -> EnumSet.of(
                    DeliveryAttemptStatus.DELIVERED,
                    DeliveryAttemptStatus.FAILED,
                    DeliveryAttemptStatus.CANCELLED)
                    .contains(targetStatus);
            case DELIVERED, FAILED, CANCELLED -> false;
        };

        if (!allowed) {
            throw new InvalidStatusTransitionException(currentStatus, targetStatus);
        }
    }
}
