package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.EvidenceDocument;
import com.leasetrack.domain.entity.DeliveryTrackingEvent;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceDocumentType;
import com.leasetrack.domain.enums.TrackingSyncStatus;
import com.leasetrack.event.model.DeliveryConfirmationCertificateRequested;
import com.leasetrack.event.model.TrackingSyncRequested;
import com.leasetrack.event.publisher.TrackingEventPublisher;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryTrackingEventRepository;
import com.leasetrack.repository.EvidenceDocumentRepository;
import com.leasetrack.storage.DocumentStorageService;
import com.leasetrack.storage.StoredDocument;
import com.leasetrack.tracking.DeliveryConfirmationCertificate;
import com.leasetrack.tracking.TrackingProvider;
import com.leasetrack.tracking.TrackingProviderErrorType;
import com.leasetrack.tracking.TrackingProviderException;
import com.leasetrack.tracking.TrackingProviderRegistry;
import com.leasetrack.tracking.TrackingSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class TrackingSyncServiceTest {

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private DeliveryTrackingEventRepository deliveryTrackingEventRepository;

    @Mock
    private EvidenceDocumentRepository evidenceDocumentRepository;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private TrackingEventPublisher trackingEventPublisher;

    @Mock
    private TrackingProvider trackingProvider;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
    private TrackingSyncService trackingSyncService;

    @BeforeEach
    void setUp() {
        trackingSyncService = new TrackingSyncService(
                deliveryAttemptRepository,
                deliveryTrackingEventRepository,
                evidenceDocumentRepository,
                documentStorageService,
                new TrackingProviderRegistry(List.of(trackingProvider)),
                trackingEventPublisher,
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class),
                new NoopTransactionManager(),
                clock,
                Duration.ofHours(4),
                Duration.ofHours(2),
                Duration.ofHours(6),
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                65536);
    }

    @Test
    void enqueueRegisteredMailTrackingPublishesDueCandidates() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123456789CA", "canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findTrackingSyncCandidateIds(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any(),
                any()))
                .thenReturn(List.of(attempt.getId()));
        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        int enqueuedCount = trackingSyncService.enqueueRegisteredMailTracking();

        assertThat(enqueuedCount).isEqualTo(1);
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.PENDING);
        assertThat(attempt.getTrackingNextCheckAt()).isEqualTo(Instant.parse("2026-05-06T12:10:00Z"));
        verify(trackingEventPublisher).publishTrackingSyncRequested(
                attempt.getId(),
                "canada-post",
                "RN123456789CA");
    }

    @Test
    void processTrackingSyncMarksDeliveredWhenProviderReturnsDelivered() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setTrackingNumber("RN123DELIVEREDCA");
        evidence.setCarrierCode("canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(trackingProvider.supportsCarrier("canada-post")).thenReturn(true);
        when(deliveryTrackingEventRepository.existsByDeliveryAttempt_IdAndEventKey(eq(attempt.getId()), any()))
                .thenReturn(false);
        when(trackingProvider.track("RN123DELIVEREDCA"))
                .thenReturn(new TrackingSummary(
                        "RN123DELIVEREDCA",
                        "Delivered",
                        "DELIVERED",
                        true,
                        Instant.parse("2026-05-06T11:30:00Z"),
                        "{\"status\":\"delivered\"}"));

        boolean synced = trackingSyncService.processTrackingSync(event(attempt));

        assertThat(synced).isTrue();
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.SUCCESS);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.DELIVERED);
        assertThat(attempt.getDeliveredAt()).isEqualTo(Instant.parse("2026-05-06T11:30:00Z"));
        assertThat(evidence.getLatestTrackingStatusCode()).isEqualTo("DELIVERED");
        assertThat(evidence.getDeliveryConfirmation()).isTrue();
        assertThat(evidence.getLatestTrackingRawPayload()).isEqualTo("{\"status\":\"delivered\"}");
        verify(deliveryTrackingEventRepository).save(any(DeliveryTrackingEvent.class));
        verify(trackingEventPublisher).publishDeliveryConfirmationCertificateRequested(
                attempt.getId(),
                "canada-post",
                "RN123DELIVEREDCA");
    }

    @Test
    void processTrackingSyncMarksUnsupportedCarrierNotApplicable() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123456789CA", "unsupported-carrier");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        boolean synced = trackingSyncService.processTrackingSync(event(attempt));

        assertThat(synced).isFalse();
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.NOT_APPLICABLE);
        assertThat(attempt.getTrackingNextCheckAt()).isEqualTo(Instant.parse("2026-05-07T12:00:00Z"));
        verify(deliveryTrackingEventRepository, never()).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void processTrackingSyncAppliesRateLimitBackoff() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123456789CA", "canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(trackingProvider.supportsCarrier("canada-post")).thenReturn(true);
        when(trackingProvider.track("RN123456789CA"))
                .thenThrow(new TrackingProviderException(
                        TrackingProviderErrorType.RATE_LIMITED,
                        429,
                        "Canada Post tracking lookup failed with HTTP 429",
                        null));

        boolean synced = trackingSyncService.processTrackingSync(event(attempt));

        assertThat(synced).isFalse();
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.FAILED);
        assertThat(attempt.getTrackingNextCheckAt()).isEqualTo(Instant.parse("2026-05-06T18:00:00Z"));
        assertThat(evidence.getLatestTrackingProviderError()).contains("RATE_LIMITED HTTP 429");
        verify(deliveryTrackingEventRepository).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void processTrackingSyncSkipsDuplicateCarrierEvents() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123DELIVEREDCA", "canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(trackingProvider.supportsCarrier("canada-post")).thenReturn(true);
        when(deliveryTrackingEventRepository.existsByDeliveryAttempt_IdAndEventKey(eq(attempt.getId()), any()))
                .thenReturn(true);
        when(trackingProvider.track("RN123DELIVEREDCA"))
                .thenReturn(new TrackingSummary(
                        "RN123DELIVEREDCA",
                        "Delivered",
                        "DELIVERED",
                        true,
                        Instant.parse("2026-05-06T11:30:00Z"),
                        "{\"status\":\"delivered\"}"));

        boolean synced = trackingSyncService.processTrackingSync(event(attempt));

        assertThat(synced).isTrue();
        verify(deliveryTrackingEventRepository, never()).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void processTrackingSyncCapsRawPayload() {
        trackingSyncService = new TrackingSyncService(
                deliveryAttemptRepository,
                deliveryTrackingEventRepository,
                evidenceDocumentRepository,
                documentStorageService,
                new TrackingProviderRegistry(List.of(trackingProvider)),
                trackingEventPublisher,
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class),
                new NoopTransactionManager(),
                clock,
                Duration.ofHours(4),
                Duration.ofHours(2),
                Duration.ofHours(6),
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                20);
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123456789CA", "canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(trackingProvider.supportsCarrier("canada-post")).thenReturn(true);
        when(deliveryTrackingEventRepository.existsByDeliveryAttempt_IdAndEventKey(eq(attempt.getId()), any()))
                .thenReturn(false);
        when(trackingProvider.track("RN123456789CA"))
                .thenReturn(new TrackingSummary(
                        "RN123456789CA",
                        "In transit",
                        "IN_TRANSIT",
                        false,
                        Instant.parse("2026-05-06T11:30:00Z"),
                        "012345678901234567890123456789"));

        trackingSyncService.processTrackingSync(event(attempt));

        assertThat(evidence.getLatestTrackingRawPayload()).hasSize(20);
        assertThat(evidence.getLatestTrackingRawPayload()).endsWith("[truncated]");
    }

    @Test
    void processDeliveryConfirmationCertificateStoresSystemGeneratedEvidenceDocument() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123DELIVEREDCA", "canada-post");
        attempt.setEvidence(evidence);
        byte[] pdf = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(trackingProvider.supportsCarrier("canada-post")).thenReturn(true);
        when(trackingProvider.fetchDeliveryConfirmationCertificate("RN123DELIVEREDCA"))
                .thenReturn(Optional.of(new DeliveryConfirmationCertificate(
                        "RN123DELIVEREDCA.pdf",
                        "application/pdf",
                        pdf)));
        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(evidenceDocumentRepository.existsByDeliveryAttempt_IdAndDocumentType(
                attempt.getId(),
                EvidenceDocumentType.DELIVERY_CONFIRMATION))
                .thenReturn(false);
        when(documentStorageService.store(any()))
                .thenReturn(new StoredDocument(
                        "s3",
                        "evidence-documents/RN123DELIVEREDCA.pdf",
                        "abc123",
                        pdf.length));

        boolean stored = trackingSyncService.processDeliveryConfirmationCertificate(certificateEvent(attempt));

        assertThat(stored).isTrue();
        assertThat(evidence.getDeliveryConfirmation()).isTrue();
        assertThat(evidence.getDeliveryConfirmationMetadata())
                .isEqualTo("evidence-documents/RN123DELIVEREDCA.pdf");
        ArgumentCaptor<EvidenceDocument> documentCaptor = ArgumentCaptor.forClass(EvidenceDocument.class);
        verify(evidenceDocumentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getDocumentType()).isEqualTo(EvidenceDocumentType.DELIVERY_CONFIRMATION);
        assertThat(documentCaptor.getValue().getUploadedByUserId()).isNull();
    }

    private DeliveryAttempt registeredMailAttempt() {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);
        attempt.setDeliveryMethod(DeliveryMethod.REGISTERED_MAIL);
        attempt.setStatus(DeliveryAttemptStatus.SENT);
        attempt.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        attempt.setUpdatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        return attempt;
    }

    private DeliveryEvidence evidence(DeliveryAttempt attempt, String trackingNumber, String carrierCode) {
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setTrackingNumber(trackingNumber);
        evidence.setCarrierCode(carrierCode);
        return evidence;
    }

    private TrackingSyncRequested event(DeliveryAttempt attempt) {
        return new TrackingSyncRequested(
                UUID.randomUUID(),
                Instant.now(clock),
                attempt.getId(),
                attempt.getEvidence().getCarrierCode(),
                attempt.getEvidence().getTrackingNumber(),
                1);
    }

    private DeliveryConfirmationCertificateRequested certificateEvent(DeliveryAttempt attempt) {
        return new DeliveryConfirmationCertificateRequested(
                UUID.randomUUID(),
                Instant.now(clock),
                attempt.getId(),
                attempt.getEvidence().getCarrierCode(),
                attempt.getEvidence().getTrackingNumber(),
                1);
    }

    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
