package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
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
import com.leasetrack.repository.NoticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private DeliveryEvidenceRepository deliveryEvidenceRepository;

    private final NoticeMapper noticeMapper = new NoticeMapper();
    private final EvidenceStrengthService evidenceStrengthService = new EvidenceStrengthService();
    @Mock
    private AuditService auditService;

    @Mock
    private NoticeEventPublisher noticeEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(
                noticeRepository,
                deliveryAttemptRepository,
                deliveryEvidenceRepository,
                evidenceStrengthService,
                auditService,
                noticeEventPublisher,
                noticeMapper,
                clock);
    }

    @Test
    void createNoticeCreatesOpenNoticeWithInitialPendingDeliveryAttempt() {
        UUID ownerUserId = UUID.randomUUID();
        UUID tenantUserId = UUID.randomUUID();
        Instant deadlineAt = Instant.parse("2026-06-01T12:00:00Z");

        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoticeResponse response = noticeService.createNotice(new CreateNoticeRequest(
                "Marie Tremblay",
                "marie@example.com",
                NoticeType.RENT_INCREASE,
                DeliveryMethod.REGISTERED_MAIL,
                ownerUserId,
                tenantUserId,
                deadlineAt,
                "Initial notice"));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(NoticeStatus.OPEN);
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
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
    void upsertDeliveryEvidenceCreatesEvidenceForAttempt() {
        Notice notice = noticeWithAttempt(DeliveryAttemptStatus.SENT);
        DeliveryAttempt attempt = notice.getDeliveryAttempts().getFirst();

        when(deliveryAttemptRepository.findByIdAndNotice_Id(attempt.getId(), notice.getId()))
                .thenReturn(Optional.of(attempt));
        when(deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId()))
                .thenReturn(Optional.empty());
        when(deliveryEvidenceRepository.save(any(DeliveryEvidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
        when(deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId())).thenReturn(Optional.of(evidence));
        when(auditService.getAuditEvents(notice.getId())).thenReturn(List.of());

        EvidencePackageResponse response = noticeService.getEvidencePackage(notice.getId());

        assertThat(response.noticeId()).isEqualTo(notice.getId());
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(response.notice().id()).isEqualTo(notice.getId());
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidence().getFirst().evidenceStrength()).isEqualTo(EvidenceStrength.STRONG);
        assertThat(response.strongestEvidenceStrength()).isEqualTo(EvidenceStrength.STRONG);
        verify(auditService).recordEvidencePackageGenerated(notice.getId());
    }

    private Specification<Notice> anySpecification() {
        return org.mockito.ArgumentMatchers.<Specification<Notice>>any();
    }

    private Notice noticeWithAttempt(DeliveryAttemptStatus attemptStatus) {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setRecipientName("Marie Tremblay");
        notice.setRecipientContactInfo("marie@example.com");
        notice.setNoticeType(NoticeType.RENT_INCREASE);
        notice.setStatus(NoticeStatus.OPEN);
        notice.setOwnerUserId(UUID.randomUUID());
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
}
