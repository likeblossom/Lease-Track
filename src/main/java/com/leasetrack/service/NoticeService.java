package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.request.CreateNoticeRequest;
import com.leasetrack.dto.request.UpdateDeliveryAttemptStatusRequest;
import com.leasetrack.dto.request.UpsertDeliveryEvidenceRequest;
import com.leasetrack.dto.response.AuditEventResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.exception.DeliveryAttemptNotFoundException;
import com.leasetrack.exception.InvalidStatusTransitionException;
import com.leasetrack.exception.NoticeNotFoundException;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryEvidenceRepository;
import com.leasetrack.repository.NoticeRepository;
import com.leasetrack.repository.specification.NoticeSpecifications;
import com.leasetrack.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
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
    private final EvidenceStrengthService evidenceStrengthService;
    private final AuditService auditService;
    private final NoticeEventPublisher noticeEventPublisher;
    private final NoticeMapper noticeMapper;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public NoticeService(
            NoticeRepository noticeRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEvidenceRepository deliveryEvidenceRepository,
            EvidenceStrengthService evidenceStrengthService,
            AuditService auditService,
            NoticeEventPublisher noticeEventPublisher,
            NoticeMapper noticeMapper,
            CurrentUserService currentUserService,
            Clock clock) {
        this.noticeRepository = noticeRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEvidenceRepository = deliveryEvidenceRepository;
        this.evidenceStrengthService = evidenceStrengthService;
        this.auditService = auditService;
        this.noticeEventPublisher = noticeEventPublisher;
        this.noticeMapper = noticeMapper;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional
    public NoticeResponse createNotice(CreateNoticeRequest request) {
        User currentUser = currentUserService.currentUser();
        assertCanCreateNotice(currentUser);
        Instant now = Instant.now(clock);

        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setRecipientName(request.recipientName());
        notice.setRecipientContactInfo(request.recipientContactInfo());
        notice.setNoticeType(request.noticeType());
        notice.setStatus(NoticeStatus.OPEN);
        notice.setOwnerUserId(currentUser.getId());
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

        Notice savedNotice = noticeRepository.save(notice);
        auditService.recordNoticeCreated(savedNotice);
        noticeEventPublisher.publishNoticeCreated(savedNotice);
        return noticeMapper.toResponse(savedNotice);
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        assertCanRead(currentUserService.currentUser(), notice);
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
                NoticeSpecifications.hasDeadlineOnOrBefore(deadlineBefore),
                accessibleNoticeSpecification(currentUserService.currentUser()));

        return noticeRepository.findAll(specification, pageable)
                .map(noticeMapper::toSummaryResponse);
    }

    @Transactional
    public NoticeResponse updateDeliveryAttemptStatus(
            UUID noticeId,
            UUID deliveryAttemptId,
            UpdateDeliveryAttemptStatusRequest request) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        assertCanManage(currentUserService.currentUser(), notice);

        DeliveryAttempt attempt = deliveryAttemptRepository.findByIdAndNotice_Id(deliveryAttemptId, noticeId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(noticeId, deliveryAttemptId));

        DeliveryAttemptStatus previousStatus = attempt.getStatus();
        DeliveryAttemptStatus targetStatus = request.status();
        validateTransition(previousStatus, targetStatus);

        Instant now = Instant.now(clock);
        attempt.setStatus(targetStatus);
        attempt.setUpdatedAt(now);

        if (targetStatus == DeliveryAttemptStatus.SENT && attempt.getSentAt() == null) {
            attempt.setSentAt(now);
        }
        if (targetStatus == DeliveryAttemptStatus.DELIVERED) {
            attempt.setDeliveredAt(now);
        }

        auditService.recordDeliveryStatusUpdated(attempt, previousStatus, targetStatus);
        noticeEventPublisher.publishDeliveryStatusUpdated(attempt, previousStatus, targetStatus);
        return noticeMapper.toResponse(attempt.getNotice());
    }

    @Transactional
    public DeliveryEvidenceResponse upsertDeliveryEvidence(
            UUID noticeId,
            UUID deliveryAttemptId,
            UpsertDeliveryEvidenceRequest request) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        assertCanManage(currentUserService.currentUser(), notice);

        DeliveryAttempt attempt = deliveryAttemptRepository.findByIdAndNotice_Id(deliveryAttemptId, noticeId)
                .orElseThrow(() -> new DeliveryAttemptNotFoundException(noticeId, deliveryAttemptId));

        Instant now = Instant.now(clock);
        Optional<DeliveryEvidence> existingEvidence = deliveryEvidenceRepository.findByDeliveryAttempt_Id(deliveryAttemptId);
        boolean created = existingEvidence.isEmpty();
        DeliveryEvidence evidence = existingEvidence
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

        DeliveryEvidence savedEvidence = deliveryEvidenceRepository.save(evidence);
        var evidenceStrength = evidenceStrengthService.classify(attempt, savedEvidence);
        auditService.recordEvidenceUpserted(savedEvidence, evidenceStrength, created);
        noticeEventPublisher.publishEvidenceUploaded(savedEvidence, evidenceStrength);
        return noticeMapper.toResponse(savedEvidence, evidenceStrength);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditLog(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        assertCanRead(currentUserService.currentUser(), notice);
        return auditService.getAuditEvents(noticeId).stream()
                .map(noticeMapper::toResponse)
                .toList();
    }

    @Transactional
    public EvidencePackageResponse getEvidencePackage(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        assertCanRead(currentUserService.currentUser(), notice);

        List<DeliveryEvidenceResponse> evidence = notice.getDeliveryAttempts().stream()
                .map(attempt -> deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId())
                        .map(deliveryEvidence -> noticeMapper.toResponse(
                                deliveryEvidence,
                                evidenceStrengthService.classify(attempt, deliveryEvidence))))
                .flatMap(Optional::stream)
                .toList();

        EvidenceStrength strongestEvidenceStrength = evidence.stream()
                .map(DeliveryEvidenceResponse::evidenceStrength)
                .reduce(EvidenceStrength.WEAK, this::stronger);

        auditService.recordEvidencePackageGenerated(noticeId);

        List<AuditEventResponse> auditEvents = auditService.getAuditEvents(noticeId).stream()
                .map(noticeMapper::toResponse)
                .toList();

        return new EvidencePackageResponse(
                noticeId,
                Instant.now(clock),
                noticeMapper.toResponse(notice),
                evidence,
                auditEvents,
                strongestEvidenceStrength);
    }

    private EvidenceStrength stronger(EvidenceStrength first, EvidenceStrength second) {
        return strengthRank(first) >= strengthRank(second) ? first : second;
    }

    private int strengthRank(EvidenceStrength evidenceStrength) {
        return switch (evidenceStrength) {
            case WEAK -> 1;
            case MEDIUM -> 2;
            case STRONG -> 3;
        };
    }

    private Specification<Notice> accessibleNoticeSpecification(User currentUser) {
        return NoticeSpecifications.accessibleTo(currentUser.getId(), currentUser.getRole());
    }

    private void assertCanCreateNotice(User user) {
        if (user.getRole() == UserRole.TENANT) {
            throw new AccessDeniedException("Tenant users cannot create notices");
        }
    }

    private void assertCanRead(User user, Notice notice) {
        if (user.getRole() == UserRole.ADMIN
                || user.getId().equals(notice.getOwnerUserId())
                || user.getId().equals(notice.getTenantUserId())) {
            return;
        }
        throw new AccessDeniedException("User cannot access this notice");
    }

    private void assertCanManage(User user, Notice notice) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        if ((user.getRole() == UserRole.LANDLORD || user.getRole() == UserRole.PROPERTY_MANAGER)
                && user.getId().equals(notice.getOwnerUserId())) {
            return;
        }
        throw new AccessDeniedException("User cannot manage this notice");
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
