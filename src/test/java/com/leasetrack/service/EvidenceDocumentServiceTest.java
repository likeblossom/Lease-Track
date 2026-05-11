package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.EvidenceDocument;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.entity.User;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceDocumentType;
import com.leasetrack.domain.enums.UserRole;
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryEvidenceRepository;
import com.leasetrack.repository.EvidenceDocumentRepository;
import com.leasetrack.security.CurrentUserService;
import com.leasetrack.storage.DocumentStorageRequest;
import com.leasetrack.storage.DocumentStorageService;
import com.leasetrack.storage.StoredDocument;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class EvidenceDocumentServiceTest {

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private DeliveryEvidenceRepository deliveryEvidenceRepository;

    @Mock
    private EvidenceDocumentRepository evidenceDocumentRepository;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private AuditService auditService;

    @Mock
    private NoticeEventPublisher noticeEventPublisher;

    @Mock
    private CurrentUserService currentUserService;

    private final NoticeMapper noticeMapper = new NoticeMapper();
    private final EvidenceStrengthService evidenceStrengthService = new EvidenceStrengthService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
    private final UUID ownerUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private EvidenceDocumentService evidenceDocumentService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserService.currentUser()).thenReturn(user());
        evidenceDocumentService = new EvidenceDocumentService(
                deliveryAttemptRepository,
                deliveryEvidenceRepository,
                evidenceDocumentRepository,
                documentStorageService,
                evidenceStrengthService,
                auditService,
                noticeEventPublisher,
                noticeMapper,
                currentUserService,
                clock);
    }

    @Test
    void uploadEvidenceDocumentStoresMetadataAndUpdatesEvidenceReference() {
        Notice notice = notice();
        DeliveryAttempt attempt = attempt(notice);
        DeliveryEvidence evidence = evidence(attempt);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "signature.pdf",
                "application/pdf",
                "signed".getBytes());

        when(deliveryAttemptRepository.findByIdAndNotice_Id(attempt.getId(), notice.getId()))
                .thenReturn(Optional.of(attempt));
        when(deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId()))
                .thenReturn(Optional.of(evidence));
        when(documentStorageService.store(any(DocumentStorageRequest.class)))
                .thenReturn(new StoredDocument("local", "notice/attempt/signature.pdf", "abc123", 6));
        when(deliveryEvidenceRepository.saveAndFlush(any(DeliveryEvidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceDocumentRepository.save(any(EvidenceDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvidenceDocumentResponse response = evidenceDocumentService.uploadEvidenceDocument(
                notice.getId(),
                attempt.getId(),
                EvidenceDocumentType.SIGNED_ACKNOWLEDGEMENT,
                file);

        assertThat(response.id()).isNotNull();
        assertThat(response.noticeId()).isEqualTo(notice.getId());
        assertThat(response.deliveryAttemptId()).isEqualTo(attempt.getId());
        assertThat(response.documentType()).isEqualTo(EvidenceDocumentType.SIGNED_ACKNOWLEDGEMENT);
        assertThat(response.originalFilename()).isEqualTo("signature.pdf");
        assertThat(response.storageProvider()).isEqualTo("local");
        assertThat(response.storageKey()).isEqualTo("notice/attempt/signature.pdf");
        assertThat(response.uploadedByUserId()).isEqualTo(ownerUserId);
        assertThat(evidence.getSignedAcknowledgementRef()).isEqualTo("notice/attempt/signature.pdf");
        verify(auditService).recordEvidenceDocumentUploaded(any(EvidenceDocument.class));
        verify(noticeEventPublisher).publishEvidenceUploaded(eq(evidence), eq(evidenceStrengthService.classify(attempt, evidence)));
    }

    @Test
    void uploadEvidenceDocumentPersistsNewEvidenceBeforeSavingDocumentReference() {
        Notice notice = notice();
        DeliveryAttempt attempt = attempt(notice);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.txt",
                "text/plain",
                "receipt".getBytes());

        when(deliveryAttemptRepository.findByIdAndNotice_Id(attempt.getId(), notice.getId()))
                .thenReturn(Optional.of(attempt));
        when(deliveryEvidenceRepository.findByDeliveryAttempt_Id(attempt.getId()))
                .thenReturn(Optional.empty());
        when(documentStorageService.store(any(DocumentStorageRequest.class)))
                .thenReturn(new StoredDocument("s3", "evidence-documents/receipt.txt", "def456", 7));
        when(deliveryEvidenceRepository.saveAndFlush(any(DeliveryEvidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceDocumentRepository.save(any(EvidenceDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvidenceDocumentResponse response = evidenceDocumentService.uploadEvidenceDocument(
                notice.getId(),
                attempt.getId(),
                EvidenceDocumentType.CARRIER_RECEIPT,
                file);

        assertThat(response.storageProvider()).isEqualTo("s3");
        assertThat(response.storageKey()).isEqualTo("evidence-documents/receipt.txt");
        assertThat(response.deliveryEvidenceId()).isNotNull();
        assertThat(attempt.getEvidence()).isNotNull();
        assertThat(attempt.getEvidence().getCarrierReceiptRef()).isEqualTo("evidence-documents/receipt.txt");
        verify(deliveryEvidenceRepository).saveAndFlush(any(DeliveryEvidence.class));
    }

    private Notice notice() {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setOwnerUserId(ownerUserId);
        return notice;
    }

    private DeliveryAttempt attempt(Notice notice) {
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);
        attempt.setDeliveryMethod(DeliveryMethod.HAND_DELIVERY_SIGNATURE);
        attempt.setStatus(DeliveryAttemptStatus.SENT);
        return attempt;
    }

    private DeliveryEvidence evidence(DeliveryAttempt attempt) {
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        attempt.setEvidence(evidence);
        return evidence;
    }

    private User user() {
        User user = new User();
        user.setId(ownerUserId);
        user.setEmail("landlord@leasetrack.dev");
        user.setPasswordHash("password");
        user.setDisplayName("Demo Landlord");
        user.setRole(UserRole.LANDLORD);
        user.setEnabled(true);
        user.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        return user;
    }
}
