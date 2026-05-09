package com.leasetrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.EvidencePackageSnapshot;
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
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.EvidencePackageAttemptResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
import com.leasetrack.dto.response.TrackingEventResponse;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.exception.DeliveryAttemptNotFoundException;
import com.leasetrack.exception.EvidencePackageGenerationException;
import com.leasetrack.exception.InvalidStatusTransitionException;
import com.leasetrack.exception.NoticeNotFoundException;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryEvidenceRepository;
import com.leasetrack.repository.EvidenceDocumentRepository;
import com.leasetrack.repository.NoticeRepository;
import com.leasetrack.repository.DeliveryTrackingEventRepository;
import com.leasetrack.repository.EvidencePackageSnapshotRepository;
import com.leasetrack.repository.UserRepository;
import com.leasetrack.repository.specification.NoticeSpecifications;
import com.leasetrack.tracking.TrackingInput;
import com.leasetrack.tracking.TrackingProviderRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.leasetrack.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NoticeService {

    private static final String EVIDENCE_PACKAGE_VERSION = "1.0";

    private final NoticeRepository noticeRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryEvidenceRepository deliveryEvidenceRepository;
    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final DeliveryTrackingEventRepository deliveryTrackingEventRepository;
    private final EvidencePackageSnapshotRepository evidencePackageSnapshotRepository;
    private final EvidenceStrengthService evidenceStrengthService;
    private final AuditService auditService;
    private final NoticeEventPublisher noticeEventPublisher;
    private final NoticeMapper noticeMapper;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TrackingProviderRegistry trackingProviderRegistry;
    private final Clock clock;

    public NoticeService(
            NoticeRepository noticeRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryEvidenceRepository deliveryEvidenceRepository,
            EvidenceDocumentRepository evidenceDocumentRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository,
            EvidencePackageSnapshotRepository evidencePackageSnapshotRepository,
            EvidenceStrengthService evidenceStrengthService,
            AuditService auditService,
            NoticeEventPublisher noticeEventPublisher,
            NoticeMapper noticeMapper,
            CurrentUserService currentUserService,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            TrackingProviderRegistry trackingProviderRegistry,
            Clock clock) {
        this.noticeRepository = noticeRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEvidenceRepository = deliveryEvidenceRepository;
        this.evidenceDocumentRepository = evidenceDocumentRepository;
        this.deliveryTrackingEventRepository = deliveryTrackingEventRepository;
        this.evidencePackageSnapshotRepository = evidencePackageSnapshotRepository;
        this.evidenceStrengthService = evidenceStrengthService;
        this.auditService = auditService;
        this.noticeEventPublisher = noticeEventPublisher;
        this.noticeMapper = noticeMapper;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.trackingProviderRegistry = trackingProviderRegistry;
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
        notice.setTenantUserId(validatedTenantUserId(request.tenantUserId()));
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

        TrackingInput trackingInput = trackingProviderRegistry
                .resolve(request.carrierName(), request.trackingNumber(), request.trackingUrl())
                .orElse(null);
        boolean hasTrackingUrl = StringUtils.hasText(request.trackingUrl());
        boolean hasCarrierAndNumber = StringUtils.hasText(request.carrierName())
                && StringUtils.hasText(request.trackingNumber());

        if (hasTrackingUrl && trackingInput == null) {
            throw new IllegalArgumentException(
                    "Unable to parse tracking URL. Provide a supported carrier and tracking number.");
        }
        if (!hasTrackingUrl && !hasCarrierAndNumber) {
            throw new IllegalArgumentException(
                    "Provide either a supported tracking URL or both carrier name and tracking number.");
        }

        if (trackingInput != null) {
            evidence.setCarrierName(StringUtils.hasText(request.carrierName())
                    ? request.carrierName().trim()
                    : trackingInput.carrierCode());
            evidence.setCarrierCode(trackingInput.carrierCode());
            evidence.setTrackingNumber(trackingInput.trackingNumber());
        } else {
            evidence.setCarrierName(request.carrierName());
            evidence.setCarrierCode(TrackingProviderRegistry.normalizeCarrierCode(request.carrierName()));
            evidence.setTrackingNumber(request.trackingNumber());
        }
        evidence.setCarrierReceiptRef(request.carrierReceiptRef());
        evidence.setDeliveryConfirmation(request.deliveryConfirmation());
        evidence.setDeliveryConfirmationMetadata(request.deliveryConfirmationMetadata());
        evidence.setSignedAcknowledgementRef(request.signedAcknowledgementRef());
        evidence.setEmailAcknowledgementRef(request.emailAcknowledgementRef());
        evidence.setEmailAcknowledgementMetadata(request.emailAcknowledgementMetadata());
        evidence.setBailiffAffidavitRef(request.bailiffAffidavitRef());
        evidence.setUpdatedAt(now);

        DeliveryEvidence savedEvidence = deliveryEvidenceRepository.save(evidence);
        savedEvidence.getDeliveryAttempt().setEvidence(savedEvidence);
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
        User currentUser = currentUserService.currentUser();
        assertCanRead(currentUser, notice);
        Instant generatedAt = Instant.now(clock);
        UUID packageId = UUID.randomUUID();

        List<DeliveryAttempt> orderedAttempts = notice.getDeliveryAttempts().stream()
                .sorted(Comparator.comparingInt(DeliveryAttempt::getAttemptNumber))
                .toList();

        Map<UUID, DeliveryEvidenceResponse> evidenceByAttemptId = orderedAttempts.stream()
                .map(attempt -> deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId())
                        .map(deliveryEvidence -> noticeMapper.toResponse(
                                deliveryEvidence,
                                evidenceStrengthService.classify(attempt, deliveryEvidence))))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(DeliveryEvidenceResponse::deliveryAttemptId, response -> response));

        List<DeliveryEvidenceResponse> evidence = orderedAttempts.stream()
                .map(attempt -> evidenceByAttemptId.get(attempt.getId()))
                .filter(Objects::nonNull)
                .toList();

        EvidenceStrength strongestEvidenceStrength = evidence.stream()
                .map(DeliveryEvidenceResponse::evidenceStrength)
                .reduce(EvidenceStrength.WEAK, this::stronger);

        List<EvidenceDocumentResponse> evidenceDocuments = evidenceDocumentRepository
                .findByNoticeIdOrderByCreatedAtAsc(noticeId).stream()
                .map(noticeMapper::toResponse)
                .toList();
        Map<UUID, List<EvidenceDocumentResponse>> documentsByAttemptId = evidenceDocuments.stream()
                .collect(Collectors.groupingBy(EvidenceDocumentResponse::deliveryAttemptId));

        List<TrackingEventResponse> trackingHistory = deliveryTrackingEventRepository
                .findByDeliveryAttempt_Notice_IdOrderByCheckedAtAsc(noticeId).stream()
                .map(noticeMapper::toResponse)
                .toList();
        Map<UUID, List<TrackingEventResponse>> trackingHistoryByAttemptId = trackingHistory.stream()
                .collect(Collectors.groupingBy(TrackingEventResponse::deliveryAttemptId));

        List<EvidencePackageAttemptResponse> attempts = orderedAttempts.stream()
                .map(attempt -> new EvidencePackageAttemptResponse(
                        noticeMapper.toResponse(attempt),
                        evidenceByAttemptId.get(attempt.getId()),
                        documentsByAttemptId.getOrDefault(attempt.getId(), List.of()),
                        trackingHistoryByAttemptId.getOrDefault(attempt.getId(), List.of())))
                .toList();

        auditService.recordEvidencePackageGenerated(noticeId);

        List<AuditEventResponse> auditEvents = auditService.getAuditEvents(noticeId).stream()
                .map(noticeMapper::toResponse)
                .toList();

        EvidencePackageCanonicalPayload canonicalPayload = new EvidencePackageCanonicalPayload(
                EVIDENCE_PACKAGE_VERSION,
                noticeId,
                currentUser.getId(),
                generatedAt,
                noticeMapper.toResponse(notice),
                attempts,
                evidence,
                evidenceDocuments,
                trackingHistory,
                auditEvents,
                strongestEvidenceStrength);
        String packageHash = sha256Hex(canonicalJson(canonicalPayload));

        EvidencePackageResponse response = new EvidencePackageResponse(
                packageId,
                EVIDENCE_PACKAGE_VERSION,
                packageHash,
                noticeId,
                currentUser.getId(),
                generatedAt,
                noticeMapper.toResponse(notice),
                attempts,
                evidence,
                evidenceDocuments,
                trackingHistory,
                auditEvents,
                strongestEvidenceStrength);
        persistEvidencePackageSnapshot(response);
        return response;
    }

    private void persistEvidencePackageSnapshot(EvidencePackageResponse response) {
        EvidencePackageSnapshot snapshot = new EvidencePackageSnapshot();
        snapshot.setId(response.packageId());
        snapshot.setNoticeId(response.noticeId());
        snapshot.setPackageVersion(response.packageVersion());
        snapshot.setPackageHash(response.packageHash());
        snapshot.setGeneratedByUserId(response.generatedByUserId());
        snapshot.setGeneratedAt(response.generatedAt());
        snapshot.setPackageJson(canonicalJson(response));
        evidencePackageSnapshotRepository.save(snapshot);
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new EvidencePackageGenerationException("Unable to serialize evidence package", ex);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new EvidencePackageGenerationException("Unable to hash evidence package", ex);
        }
    }

    private record EvidencePackageCanonicalPayload(
            String packageVersion,
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

    private UUID validatedTenantUserId(UUID tenantUserId) {
        if (tenantUserId == null) {
            return null;
        }
        User tenant = userRepository.findById(tenantUserId)
                .orElseThrow(() -> new AccessDeniedException("Assigned tenant user does not exist"));
        if (tenant.getRole() != UserRole.TENANT || !tenant.isEnabled()) {
            throw new AccessDeniedException("Notices can only be assigned to enabled tenant users");
        }
        return tenant.getId();
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
