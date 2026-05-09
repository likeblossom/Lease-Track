package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryTrackingEvent;
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
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.exception.InvalidStatusTransitionException;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryEvidenceRepository;
import com.leasetrack.repository.EvidenceDocumentRepository;
import com.leasetrack.repository.EvidencePackageSnapshotRepository;
import com.leasetrack.repository.NoticeRepository;
import com.leasetrack.repository.DeliveryTrackingEventRepository;
import com.leasetrack.repository.UserRepository;
import com.leasetrack.security.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private DeliveryEvidenceRepository deliveryEvidenceRepository;

    @Mock
    private EvidenceDocumentRepository evidenceDocumentRepository;

    @Mock
    private DeliveryTrackingEventRepository deliveryTrackingEventRepository;

    @Mock
    private EvidencePackageSnapshotRepository evidencePackageSnapshotRepository;

    private final NoticeMapper noticeMapper = new NoticeMapper();
    private final EvidenceStrengthService evidenceStrengthService = new EvidenceStrengthService();
    @Mock
    private AuditService auditService;

    @Mock
    private NoticeEventPublisher noticeEventPublisher;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UUID landlordUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserService.currentUser()).thenReturn(user(landlordUserId, UserRole.LANDLORD));
        noticeService = new NoticeService(
                noticeRepository,
                deliveryAttemptRepository,
                deliveryEvidenceRepository,
                evidenceDocumentRepository,
                deliveryTrackingEventRepository,
                evidencePackageSnapshotRepository,
                evidenceStrengthService,
                auditService,
                noticeEventPublisher,
                noticeMapper,
                currentUserService,
                userRepository,
                objectMapper,
                clock);
    }

    @Test
    void createNoticeCreatesOpenNoticeWithInitialPendingDeliveryAttempt() {
        UUID tenantUserId = UUID.randomUUID();
        Instant deadlineAt = Instant.parse("2026-06-01T12:00:00Z");

        when(userRepository.findById(tenantUserId)).thenReturn(Optional.of(user(tenantUserId, UserRole.TENANT)));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoticeResponse response = noticeService.createNotice(new CreateNoticeRequest(
                "Marie Tremblay",
                "marie@example.com",
                NoticeType.RENT_INCREASE,
                DeliveryMethod.REGISTERED_MAIL,
                tenantUserId,
                deadlineAt,
                "Initial notice"));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(NoticeStatus.OPEN);
        assertThat(response.ownerUserId()).isEqualTo(landlordUserId);
        assertThat(response.tenantUserId()).isEqualTo(tenantUserId);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(response.deliveryAttempts()).hasSize(1);
        assertThat(response.deliveryAttempts().getFirst().deliveryMethod()).isEqualTo(DeliveryMethod.REGISTERED_MAIL);
        assertThat(response.deliveryAttempts().getFirst().status()).isEqualTo(DeliveryAttemptStatus.PENDING);
        assertThat(response.deliveryAttempts().getFirst().deadlineAt()).isEqualTo(deadlineAt);
        verify(auditService).recordNoticeCreated(any(Notice.class));
        verify(noticeEventPublisher).publishNoticeCreated(any(Notice.class));
    }

    @Test
    void createNoticeRejectsTenantAssignmentToNonTenantUser() {
        UUID managerUserId = UUID.randomUUID();
        when(userRepository.findById(managerUserId)).thenReturn(Optional.of(user(managerUserId, UserRole.PROPERTY_MANAGER)));

        assertThatThrownBy(() -> noticeService.createNotice(new CreateNoticeRequest(
                "Marie Tremblay",
                "marie@example.com",
                NoticeType.RENT_INCREASE,
                DeliveryMethod.REGISTERED_MAIL,
                managerUserId,
                Instant.parse("2026-06-01T12:00:00Z"),
                "Initial notice")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("enabled tenant users");
    }

    @Test
    void listNoticesReturnsPagedSummaryResponses() {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setRecipientName("Marie Tremblay");
        notice.setRecipientContactInfo("marie@example.com");
        notice.setNoticeType(NoticeType.RENT_INCREASE);
        notice.setStatus(NoticeStatus.OPEN);
        notice.setOwnerUserId(UUID.randomUUID());
        notice.setCreatedAt(Instant.parse("2026-05-06T12:00:00Z"));
        notice.setUpdatedAt(Instant.parse("2026-05-06T12:00:00Z"));

        PageRequest pageable = PageRequest.of(0, 20);
        when(noticeRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notice), pageable, 1));

        Page<NoticeSummaryResponse> response = noticeService.listNotices(
                NoticeStatus.OPEN,
                NoticeType.RENT_INCREASE,
                DeliveryMethod.REGISTERED_MAIL,
                null,
                null,
                pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().getFirst().id()).isEqualTo(notice.getId());
        assertThat(response.getContent().getFirst().recipientName()).isEqualTo("Marie Tremblay");
        assertThat(response.getContent().getFirst().noticeType()).isEqualTo(NoticeType.RENT_INCREASE);
    }

    @Test
    void updateDeliveryAttemptStatusMovesPendingAttemptToSent() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.PENDING);
        DeliveryAttempt attempt = notice.getDeliveryAttempts().getFirst();

        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(deliveryAttemptRepository.findByIdAndNotice_Id(attempt.getId(), notice.getId()))
                .thenReturn(Optional.of(attempt));

        NoticeResponse response = noticeService.updateDeliveryAttemptStatus(
                notice.getId(),
                attempt.getId(),
                new UpdateDeliveryAttemptStatusRequest(DeliveryAttemptStatus.SENT));

        assertThat(response.deliveryAttempts().getFirst().status()).isEqualTo(DeliveryAttemptStatus.SENT);
        assertThat(response.deliveryAttempts().getFirst().sentAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(response.deliveryAttempts().getFirst().updatedAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        verify(auditService).recordDeliveryStatusUpdated(
                attempt,
                DeliveryAttemptStatus.PENDING,
                DeliveryAttemptStatus.SENT);
        verify(noticeEventPublisher).publishDeliveryStatusUpdated(
                attempt,
                DeliveryAttemptStatus.PENDING,
                DeliveryAttemptStatus.SENT);
    }

    @Test
    void updateDeliveryAttemptStatusRejectsInvalidTransition() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.DELIVERED);
        DeliveryAttempt attempt = notice.getDeliveryAttempts().getFirst();

        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(deliveryAttemptRepository.findByIdAndNotice_Id(attempt.getId(), notice.getId()))
                .thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> noticeService.updateDeliveryAttemptStatus(
                notice.getId(),
                attempt.getId(),
                new UpdateDeliveryAttemptStatusRequest(DeliveryAttemptStatus.SENT)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("DELIVERED -> SENT");
    }

    @Test
    void tenantCannotManageNoticeEvenWhenAssignedToNotice() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.PENDING);
        UUID tenantUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        notice.setTenantUserId(tenantUserId);
        DeliveryAttempt attempt = notice.getDeliveryAttempts().getFirst();

        when(currentUserService.currentUser()).thenReturn(user(tenantUserId, UserRole.TENANT));
        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> noticeService.updateDeliveryAttemptStatus(
                notice.getId(),
                attempt.getId(),
                new UpdateDeliveryAttemptStatusRequest(DeliveryAttemptStatus.SENT)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot manage");
    }

    @Test
    void tenantCanReadAssignedNotice() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.PENDING);
        UUID tenantUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        notice.setTenantUserId(tenantUserId);

        when(currentUserService.currentUser()).thenReturn(user(tenantUserId, UserRole.TENANT));
        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));

        NoticeResponse response = noticeService.getNotice(notice.getId());

        assertThat(response.id()).isEqualTo(notice.getId());
        assertThat(response.tenantUserId()).isEqualTo(tenantUserId);
    }

    @Test
    void tenantCannotReadUnassignedNotice() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.PENDING);
        notice.setTenantUserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        UUID otherTenantUserId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(currentUserService.currentUser()).thenReturn(user(otherTenantUserId, UserRole.TENANT));
        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> noticeService.getNotice(notice.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot access");
    }

    @Test
    void upsertDeliveryEvidenceCreatesEvidenceForAttempt() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.SENT);
        DeliveryAttempt attempt = notice.getDeliveryAttempts().getFirst();

        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(deliveryAttemptRepository.findByIdAndNotice_Id(attempt.getId(), notice.getId()))
                .thenReturn(Optional.of(attempt));
        when(deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId()))
                .thenReturn(Optional.empty());
        when(deliveryEvidenceRepository.save(any(DeliveryEvidence.class)))
                .thenAnswer(invocation -> managedCopy(invocation.getArgument(0)));

        DeliveryEvidenceResponse response = noticeService.upsertDeliveryEvidence(
                notice.getId(),
                attempt.getId(),
                new UpsertDeliveryEvidenceRequest(
                        "RN123456789CA",
                        "Canada Post",
                        "receipts/rn123.pdf",
                        true,
                        "Delivered to recipient",
                        null,
                        null,
                        null,
                        null));

        assertThat(response.id()).isNotNull();
        assertThat(response.deliveryAttemptId()).isEqualTo(attempt.getId());
        assertThat(response.trackingNumber()).isEqualTo("RN123456789CA");
        assertThat(response.carrierName()).isEqualTo("Canada Post");
        assertThat(response.deliveryConfirmation()).isTrue();
        assertThat(response.evidenceStrength()).isNotNull();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        ArgumentCaptor<DeliveryEvidence> savedEvidenceCaptor = ArgumentCaptor.forClass(DeliveryEvidence.class);
        verify(deliveryEvidenceRepository).save(savedEvidenceCaptor.capture());
        assertThat(attempt.getEvidence()).isNotNull();
        assertThat(attempt.getEvidence()).isNotSameAs(savedEvidenceCaptor.getValue());
        assertThat(attempt.getEvidence().getId()).isEqualTo(savedEvidenceCaptor.getValue().getId());
        verify(auditService).recordEvidenceUpserted(
                any(DeliveryEvidence.class),
                eq(response.evidenceStrength()),
                eq(true));
        verify(noticeEventPublisher).publishEvidenceUploaded(
                any(DeliveryEvidence.class),
                eq(response.evidenceStrength()));
    }

    @Test
    void getEvidencePackageReturnsNoticeEvidenceAuditAndStrongestStrength() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.DELIVERED);
        DeliveryAttempt attempt = notice.getDeliveryAttempts().getFirst();
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setTrackingNumber("RN123456789CA");
        evidence.setDeliveryConfirmation(true);
        evidence.setCreatedAt(Instant.parse("2026-05-06T12:00:00Z"));
        evidence.setUpdatedAt(Instant.parse("2026-05-06T12:00:00Z"));
        DeliveryTrackingEvent trackingEvent = new DeliveryTrackingEvent();
        trackingEvent.setId(UUID.randomUUID());
        trackingEvent.setDeliveryAttempt(attempt);
        trackingEvent.setTrackingNumber("RN123456789CA");
        trackingEvent.setStatus("Delivered");
        trackingEvent.setStatusCode("DELIVERED");
        trackingEvent.setDelivered(true);
        trackingEvent.setEventAt(Instant.parse("2026-05-06T11:30:00Z"));
        trackingEvent.setCheckedAt(Instant.parse("2026-05-06T12:00:00Z"));

        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId())).thenReturn(Optional.of(evidence));
        when(evidenceDocumentRepository.findByNoticeIdOrderByCreatedAtAsc(notice.getId())).thenReturn(List.of());
        when(deliveryTrackingEventRepository.findByDeliveryAttempt_Notice_IdOrderByCheckedAtAsc(notice.getId()))
                .thenReturn(List.of(trackingEvent));
        when(auditService.getAuditEvents(notice.getId())).thenReturn(List.of());

        EvidencePackageResponse response = noticeService.getEvidencePackage(notice.getId());

        assertThat(response.noticeId()).isEqualTo(notice.getId());
        assertThat(response.packageId()).isNotNull();
        assertThat(response.packageVersion()).isEqualTo("1.0");
        assertThat(response.packageHash()).hasSize(64);
        assertThat(response.generatedByUserId()).isEqualTo(landlordUserId);
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(response.notice().id()).isEqualTo(notice.getId());
        assertThat(response.attempts()).hasSize(1);
        assertThat(response.attempts().getFirst().attempt().id()).isEqualTo(attempt.getId());
        assertThat(response.attempts().getFirst().evidence().id()).isEqualTo(evidence.getId());
        assertThat(response.attempts().getFirst().trackingHistory()).hasSize(1);
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidenceDocuments()).isEmpty();
        assertThat(response.trackingHistory()).hasSize(1);
        assertThat(response.trackingHistory().getFirst().statusCode()).isEqualTo("DELIVERED");
        assertThat(response.evidence().getFirst().evidenceStrength()).isEqualTo(EvidenceStrength.STRONG);
        assertThat(response.strongestEvidenceStrength()).isEqualTo(EvidenceStrength.STRONG);
        ArgumentCaptor<EvidencePackageSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(EvidencePackageSnapshot.class);
        verify(evidencePackageSnapshotRepository).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getId()).isEqualTo(response.packageId());
        assertThat(snapshotCaptor.getValue().getPackageHash()).isEqualTo(response.packageHash());
        assertThat(snapshotCaptor.getValue().getPackageJson()).contains(response.packageHash());
        verify(auditService).recordEvidencePackageGenerated(notice.getId());
    }

    private Specification<Notice> anySpecification() {
        return org.mockito.ArgumentMatchers.<Specification<Notice>>any();
    }

    private DeliveryEvidence managedCopy(DeliveryEvidence evidence) {
        DeliveryEvidence managedEvidence = new DeliveryEvidence();
        managedEvidence.setId(evidence.getId());
        managedEvidence.setDeliveryAttempt(evidence.getDeliveryAttempt());
        managedEvidence.setTrackingNumber(evidence.getTrackingNumber());
        managedEvidence.setCarrierName(evidence.getCarrierName());
        managedEvidence.setCarrierReceiptRef(evidence.getCarrierReceiptRef());
        managedEvidence.setDeliveryConfirmation(evidence.getDeliveryConfirmation());
        managedEvidence.setDeliveryConfirmationMetadata(evidence.getDeliveryConfirmationMetadata());
        managedEvidence.setSignedAcknowledgementRef(evidence.getSignedAcknowledgementRef());
        managedEvidence.setEmailAcknowledgementRef(evidence.getEmailAcknowledgementRef());
        managedEvidence.setEmailAcknowledgementMetadata(evidence.getEmailAcknowledgementMetadata());
        managedEvidence.setBailiffAffidavitRef(evidence.getBailiffAffidavitRef());
        managedEvidence.setCreatedAt(evidence.getCreatedAt());
        managedEvidence.setUpdatedAt(evidence.getUpdatedAt());
        return managedEvidence;
    }

    private Notice noticeWithAttempt(DeliveryAttemptStatus attemptStatus) {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setRecipientName("Marie Tremblay");
        notice.setRecipientContactInfo("marie@example.com");
        notice.setNoticeType(NoticeType.RENT_INCREASE);
        notice.setStatus(NoticeStatus.OPEN);
        notice.setOwnerUserId(landlordUserId);
        notice.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        notice.setUpdatedAt(Instant.parse("2026-05-01T12:00:00Z"));

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);
        attempt.setAttemptNumber(1);
        attempt.setDeliveryMethod(DeliveryMethod.REGISTERED_MAIL);
        attempt.setStatus(attemptStatus);
        attempt.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        attempt.setUpdatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        notice.getDeliveryAttempts().add(attempt);
        return notice;
    }

    private User user(UUID id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(role.name().toLowerCase() + "@leasetrack.dev");
        user.setPasswordHash("password");
        user.setDisplayName(role.name());
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        return user;
    }
}
