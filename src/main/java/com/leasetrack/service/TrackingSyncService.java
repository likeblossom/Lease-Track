package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.TrackingSyncStatus;
import com.leasetrack.repository.DeliveryAttemptRepository;
import com.leasetrack.tracking.TrackingProvider;
import com.leasetrack.tracking.TrackingSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TrackingSyncService {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final TrackingProvider trackingProvider;
    private final Clock clock;

    public TrackingSyncService(
            DeliveryAttemptRepository deliveryAttemptRepository,
            TrackingProvider trackingProvider,
            Clock clock) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.trackingProvider = trackingProvider;
        this.clock = clock;
    }

    @Transactional
    public int syncRegisteredMailTracking() {
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByDeliveryMethodAndStatusIn(
                DeliveryMethod.REGISTERED_MAIL,
                EnumSet.of(DeliveryAttemptStatus.PENDING, DeliveryAttemptStatus.SENT));

        int syncedCount = 0;
        for (DeliveryAttempt attempt : attempts) {
            DeliveryEvidence evidence = attempt.getEvidence();
            if (evidence == null || !StringUtils.hasText(evidence.getTrackingNumber())) {
                attempt.setTrackingSyncStatus(TrackingSyncStatus.NOT_APPLICABLE);
                continue;
            }

            attempt.setTrackingSyncStatus(TrackingSyncStatus.PENDING);
            try {
                TrackingSummary summary = trackingProvider.track(evidence.getTrackingNumber());
                applyTrackingSummary(attempt, evidence, summary);
                attempt.setTrackingSyncStatus(TrackingSyncStatus.SUCCESS);
                syncedCount++;
            } catch (RuntimeException ex) {
                attempt.setTrackingSyncStatus(TrackingSyncStatus.FAILED);
                attempt.setLastTrackingCheckedAt(Instant.now(clock));
            }
        }
        return syncedCount;
    }

    private void applyTrackingSummary(
            DeliveryAttempt attempt,
            DeliveryEvidence evidence,
            TrackingSummary summary) {
        Instant now = Instant.now(clock);
        evidence.setLatestTrackingStatus(summary.status());
        evidence.setLatestTrackingStatusCode(summary.statusCode());
        evidence.setLatestTrackingEventAt(summary.eventAt());
        evidence.setDeliveryConfirmation(summary.delivered());
        attempt.setLastTrackingCheckedAt(now);
        attempt.setUpdatedAt(now);

        if (summary.delivered() && attempt.getStatus() != DeliveryAttemptStatus.DELIVERED) {
            attempt.setStatus(DeliveryAttemptStatus.DELIVERED);
            attempt.setDeliveredAt(summary.eventAt());
        }
    }
}
