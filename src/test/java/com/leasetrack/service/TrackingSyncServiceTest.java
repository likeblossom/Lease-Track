package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.leasetrack.tracking.TrackingSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                trackingProvider,
                clock);
    }

    @Test
    void syncRegisteredMailTrackingMarksDeliveredWhenProviderReturnsDelivered() {
        DeliveryAttempt attempt = registeredMailAttempt();
        DeliveryEvidence evidence = new DeliveryEvidence();
        evidence.setId(UUID.randomUUID());
        evidence.setDeliveryAttempt(attempt);
        evidence.setTrackingNumber("RN123DELIVEREDCA");
        attempt.setEvidence(evidence);

        when(deliveryAttemptRepository.findByDeliveryMethodAndStatusIn(
                eq(DeliveryMethod.REGISTERED_MAIL),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any()))
                .thenReturn(List.of(attempt));
        when(trackingProvider.track("RN123DELIVEREDCA"))
                .thenReturn(new TrackingSummary(
                        "RN123DELIVEREDCA",
                        "Delivered",
                        "DELIVERED",
                        true,
                        Instant.parse("2026-05-06T11:30:00Z")));

        int syncedCount = trackingSyncService.syncRegisteredMailTracking();

        assertThat(syncedCount).isEqualTo(1);
        assertThat(attempt.getTrackingSyncStatus()).isEqualTo(TrackingSyncStatus.SUCCESS);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryAttemptStatus.DELIVERED);
        assertThat(attempt.getDeliveredAt()).isEqualTo(Instant.parse("2026-05-06T11:30:00Z"));
        assertThat(evidence.getLatestTrackingStatusCode()).isEqualTo("DELIVERED");
        assertThat(evidence.getDeliveryConfirmation()).isTrue();
        verify(deliveryTrackingEventRepository).save(any(DeliveryTrackingEvent.class));
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
}
