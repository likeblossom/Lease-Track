package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryTrackingEvent;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.TrackingSyncStatus;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.repository.DeliveryTrackingEventRepository;
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
    private TrackingProvider trackingProvider;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
    private TrackingSyncService trackingSyncService;

    @BeforeEach
    void setUp() {
        trackingSyncService = new TrackingSyncService(
                deliveryAttemptRepository,
                deliveryTrackingEventRepository,
                new TrackingProviderRegistry(List.of(trackingProvider)),
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
    void syncRegisteredMailTrackingMarksDeliveredWhenProviderReturnsDelivered() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setTrackingNumber("RN123DELIVEREDCA");
        evidence.setCarrierCode("canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findTrackingSyncCandidateIds(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any(),
                any()))
                .thenReturn(List.of(attempt.getId()));
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

        int syncedCount = trackingSyncService.syncRegisteredMailTracking();

        assertThat(syncedCount).isEqualTo(1);
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.SUCCESS);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.DELIVERED);
        assertThat(attempt.getDeliveredAt()).isEqualTo(Instant.parse("2026-05-06T11:30:00Z"));
        assertThat(evidence.getLatestTrackingStatusCode()).isEqualTo("DELIVERED");
        assertThat(evidence.getDeliveryConfirmation()).isTrue();
        assertThat(evidence.getLatestTrackingRawPayload()).isEqualTo("{\"status\":\"delivered\"}");
        verify(deliveryTrackingEventRepository).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void syncRegisteredMailTrackingMarksUnsupportedCarrierNotApplicable() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123456789CA", "unsupported-carrier");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findTrackingSyncCandidateIds(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any(),
                any()))
                .thenReturn(List.of(attempt.getId()));
        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        int syncedCount = trackingSyncService.syncRegisteredMailTracking();

        assertThat(syncedCount).isZero();
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.NOT_APPLICABLE);
        assertThat(attempt.getTrackingNextCheckAt()).isEqualTo(Instant.parse("2026-05-07T12:00:00Z"));
        verify(deliveryTrackingEventRepository, never()).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void syncRegisteredMailTrackingAppliesRateLimitBackoff() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123456789CA", "canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findTrackingSyncCandidateIds(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any(),
                any()))
                .thenReturn(List.of(attempt.getId()));
        when(deliveryAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(trackingProvider.supportsCarrier("canada-post")).thenReturn(true);
        when(trackingProvider.track("RN123456789CA"))
                .thenThrow(new TrackingProviderException(
                        TrackingProviderErrorType.RATE_LIMITED,
                        429,
                        "Canada Post tracking lookup failed with HTTP 429",
                        null));

        int syncedCount = trackingSyncService.syncRegisteredMailTracking();

        assertThat(syncedCount).isZero();
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.FAILED);
        assertThat(attempt.getTrackingNextCheckAt()).isEqualTo(Instant.parse("2026-05-06T18:00:00Z"));
        assertThat(evidence.getLatestTrackingProviderError()).contains("RATE_LIMITED HTTP 429");
        verify(deliveryTrackingEventRepository).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void syncRegisteredMailTrackingSkipsDuplicateCarrierEvents() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = evidence(attempt, "RN123DELIVEREDCA", "canada-post");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findTrackingSyncCandidateIds(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any(),
                any()))
                .thenReturn(List.of(attempt.getId()));
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

        int syncedCount = trackingSyncService.syncRegisteredMailTracking();

        assertThat(syncedCount).isEqualTo(1);
        verify(deliveryTrackingEventRepository, never()).save(any(DeliveryTrackingEvent.class));
    }

    @Test
    void syncRegisteredMailTrackingCapsRawPayload() {
        trackingSyncService = new TrackingSyncService(
                deliveryAttemptRepository,
                deliveryTrackingEventRepository,
                new TrackingProviderRegistry(List.of(trackingProvider)),
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

        when(deliveryAttemptRepository.findTrackingSyncCandidateIds(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any(),
                any()))
                .thenReturn(List.of(attempt.getId()));
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

        trackingSyncService.syncRegisteredMailTracking();

        assertThat(evidence.getLatestTrackingRawPayload()).hasSize(20);
        assertThat(evidence.getLatestTrackingRawPayload()).endsWith("[truncated]");
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
