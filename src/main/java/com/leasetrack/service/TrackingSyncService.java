package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.DeliveryTrackingEvent;
import com.leasetrack.domain.entity.EvidenceDocument;
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
import com.leasetrack.storage.DocumentStorageRequest;
import com.leasetrack.storage.DocumentStorageService;
import com.leasetrack.storage.StoredDocument;
import com.leasetrack.tracking.DeliveryConfirmationCertificate;
import com.leasetrack.tracking.TrackingProvider;
import com.leasetrack.tracking.TrackingProviderErrorType;
import com.leasetrack.tracking.TrackingProviderException;
import com.leasetrack.tracking.TrackingProviderRegistry;
import com.leasetrack.tracking.TrackingEvent;
import com.leasetrack.tracking.TrackingSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class TrackingSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackingSyncService.class);

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryTrackingEventRepository deliveryTrackingEventRepository;
    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final DocumentStorageService documentStorageService;
    private final TrackingProviderRegistry trackingProviderRegistry;
    private final TrackingEventPublisher trackingEventPublisher;
    private final Optional<MeterRegistry> meterRegistry;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration successDelay;
    private final Duration transientFailureDelay;
    private final Duration rateLimitDelay;
    private final Duration permanentFailureDelay;
    private final Duration syncLeaseDuration;
    private final int maxRawPayloadChars;

    public TrackingSyncService(
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository,
            EvidenceDocumentRepository evidenceDocumentRepository,
            DocumentStorageService documentStorageService,
            TrackingProviderRegistry trackingProviderRegistry,
            TrackingEventPublisher trackingEventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${app.tracking.sync.success-delay:PT4H}") Duration successDelay,
            @Value("${app.tracking.sync.transient-failure-delay:PT2H}") Duration transientFailureDelay,
            @Value("${app.tracking.sync.rate-limit-delay:PT6H}") Duration rateLimitDelay,
            @Value("${app.tracking.sync.permanent-failure-delay:PT24H}") Duration permanentFailureDelay,
            @Value("${app.tracking.sync.lease-duration:PT10M}") Duration syncLeaseDuration,
            @Value("${app.tracking.sync.max-raw-payload-chars:65536}") int maxRawPayloadChars) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryTrackingEventRepository = deliveryTrackingEventRepository;
        this.evidenceDocumentRepository = evidenceDocumentRepository;
        this.documentStorageService = documentStorageService;
        this.trackingProviderRegistry = trackingProviderRegistry;
        this.trackingEventPublisher = trackingEventPublisher;
        this.meterRegistry = Optional.ofNullable(meterRegistryProvider.getIfAvailable());
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.successDelay = successDelay;
        this.transientFailureDelay = transientFailureDelay;
        this.rateLimitDelay = rateLimitDelay;
        this.permanentFailureDelay = permanentFailureDelay;
        this.syncLeaseDuration = syncLeaseDuration;
        this.maxRawPayloadChars = maxRawPayloadChars;
    }

    public int enqueueRegisteredMailTracking() {
        List<UUID> attemptIds = deliveryAttemptRepository.findTrackingSyncCandidateIds(
                DeliveryMethod.REGISTERED_MAIL,
                EnumSet.of(DeliveryAttemptStatus.PENDING, DeliveryAttemptStatus.SENT),
                Instant.now(clock));

        int enqueuedCount = 0;
        for (UUID attemptId : attemptIds) {
            Optional<TrackingSyncCandidate> candidate = transactionTemplate.execute(status -> loadCandidate(attemptId));
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }

            TrackingSyncCandidate trackingCandidate = candidate.get();
            trackingEventPublisher.publishTrackingSyncRequested(
                    trackingCandidate.attemptId(),
                    trackingCandidate.carrierCode(),
                    trackingCandidate.trackingNumber());
            enqueuedCount++;
        }
        return enqueuedCount;
    }

    public int syncRegisteredMailTracking() {
        return enqueueRegisteredMailTracking();
    }

    public boolean processTrackingSync(TrackingSyncRequested event) {
        TrackingSyncCandidate trackingCandidate = new TrackingSyncCandidate(
                event.deliveryAttemptId(),
                event.carrierCode(),
                event.trackingNumber());
        Optional<TrackingProvider> provider = trackingProviderRegistry.findByCarrier(trackingCandidate.carrierCode());
        if (provider.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> markNotApplicable(trackingCandidate.attemptId()));
            recordOutcome("unsupported_carrier", trackingCandidate.carrierCode(), trackingCandidate.attemptId(), null);
            return false;
        }

        try {
            TrackingSummary summary = provider.get().track(trackingCandidate.trackingNumber());
            transactionTemplate.executeWithoutResult(status -> applySuccess(trackingCandidate.attemptId(), summary));
            recordOutcome(
                    summary.delivered() ? "delivered" : "success",
                    trackingCandidate.carrierCode(),
                    trackingCandidate.attemptId(),
                    null);
            if (summary.delivered()) {
                trackingEventPublisher.publishDeliveryConfirmationCertificateRequested(
                        trackingCandidate.attemptId(),
                        trackingCandidate.carrierCode(),
                        trackingCandidate.trackingNumber());
            }
            return true;
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> applyFailure(trackingCandidate, ex));
            recordOutcome(outcomeFor(ex), trackingCandidate.carrierCode(), trackingCandidate.attemptId(), ex);
            return false;
        }
    }

    public boolean processDeliveryConfirmationCertificate(DeliveryConfirmationCertificateRequested event) {
        Optional<TrackingProvider> provider = trackingProviderRegistry.findByCarrier(event.carrierCode());
        if (provider.isEmpty()) {
            recordOutcome("unsupported_certificate_carrier", event.carrierCode(), event.deliveryAttemptId(), null);
            return false;
        }

        try {
            Optional<DeliveryConfirmationCertificate> certificate =
                    provider.get().fetchDeliveryConfirmationCertificate(event.trackingNumber());
            if (certificate.isEmpty()) {
                recordOutcome("certificate_unavailable", event.carrierCode(), event.deliveryAttemptId(), null);
                return false;
            }
            transactionTemplate.executeWithoutResult(
                    status -> storeDeliveryConfirmationCertificate(event.deliveryAttemptId(), certificate.get()));
            recordOutcome("certificate_stored", event.carrierCode(), event.deliveryAttemptId(), null);
            return true;
        } catch (RuntimeException ex) {
            recordOutcome(outcomeFor(ex), event.carrierCode(), event.deliveryAttemptId(), ex);
            return false;
        }
    }

    private Optional<TrackingSyncCandidate> loadCandidate(UUID attemptId) {
        return deliveryAttemptRepository.findById(attemptId)
                .map(attempt -> {
                    DeliveryEvidence evidence = attempt.getEvidence();
                    if (evidence == null || !StringUtils.hasText(evidence.getTrackingNumber())
                            || !StringUtils.hasText(evidence.getCarrierCode())) {
                        return null;
                    }
                    Instant now = Instant.now(clock);
                    attempt.setTrackingSyncStatus(TrackingSyncStatus.PENDING);
                    attempt.setTrackingNextCheckAt(now.plus(syncLeaseDuration));
                    attempt.setUpdatedAt(now);
                    return new TrackingSyncCandidate(
                            attempt.getId(),
                            evidence.getCarrierCode(),
                            evidence.getTrackingNumber());
                });
    }

    private void applySuccess(UUID attemptId, TrackingSummary summary) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery attempt not found"));
        DeliveryEvidence evidence = attempt.getEvidence();
        applyTrackingSummary(attempt, evidence, summary);
        evidence.setLatestTrackingRawPayload(cap(summary.rawProviderPayload()));
        evidence.setLatestTrackingProviderError(null);
        saveTrackingEvents(attempt, summary, null);
        attempt.setTrackingSyncStatus(TrackingSyncStatus.SUCCESS);
    }

    private void storeDeliveryConfirmationCertificate(
            UUID attemptId,
            DeliveryConfirmationCertificate certificate) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery attempt not found"));
        DeliveryEvidence evidence = attempt.getEvidence();
        if (evidence == null) {
            throw new IllegalStateException("Delivery attempt does not have delivery evidence");
        }
        if (StringUtils.hasText(evidence.getDeliveryConfirmationMetadata())
                || evidenceDocumentRepository.existsByDeliveryAttempt_IdAndDocumentType(
                        attemptId,
                        EvidenceDocumentType.DELIVERY_CONFIRMATION)) {
            return;
        }

        Instant now = Instant.now(clock);
        UUID documentId = UUID.randomUUID();
        StoredDocument storedDocument = documentStorageService.store(new DocumentStorageRequest(
                attempt.getNotice().getId(),
                attempt.getId(),
                documentId,
                certificate.filename(),
                certificate.contentType(),
                new ByteArrayInputStream(certificate.content())));

        evidence.setDeliveryConfirmation(true);
        evidence.setDeliveryConfirmationMetadata(storedDocument.storageKey());
        evidence.setUpdatedAt(now);

        EvidenceDocument document = new EvidenceDocument();
        document.setId(documentId);
        document.setNoticeId(attempt.getNotice().getId());
        document.setDeliveryAttempt(attempt);
        document.setDeliveryEvidence(evidence);
        document.setDocumentType(EvidenceDocumentType.DELIVERY_CONFIRMATION);
        document.setOriginalFilename(certificate.filename());
        document.setContentType(certificate.contentType());
        document.setSizeBytes(storedDocument.sizeBytes());
        document.setStorageProvider(storedDocument.storageProvider());
        document.setStorageKey(storedDocument.storageKey());
        document.setSha256Checksum(storedDocument.sha256Checksum());
        document.setUploadedByUserId(null);
        document.setCreatedAt(now);
        evidenceDocumentRepository.save(document);
    }

    private void applyFailure(TrackingSyncCandidate candidate, RuntimeException ex) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(candidate.attemptId())
                .orElseThrow(() -> new IllegalArgumentException("Delivery attempt not found"));
        DeliveryEvidence evidence = attempt.getEvidence();
        Instant now = Instant.now(clock);
        String errorMessage = cap(errorMessage(ex));
        evidence.setLatestTrackingProviderError(errorMessage);
        evidence.setUpdatedAt(now);
        attempt.setTrackingSyncStatus(TrackingSyncStatus.FAILED);
        attempt.setLastTrackingCheckedAt(now);
        attempt.setTrackingNextCheckAt(now.plus(delayFor(ex)));
        attempt.setUpdatedAt(now);
        saveTrackingEvent(attempt, candidate.trackingNumber(), errorMessage);
    }

    private void markNotApplicable(UUID attemptId) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery attempt not found"));
        Instant now = Instant.now(clock);
        attempt.setTrackingSyncStatus(TrackingSyncStatus.NOT_APPLICABLE);
        attempt.setLastTrackingCheckedAt(now);
        attempt.setTrackingNextCheckAt(now.plus(permanentFailureDelay));
        attempt.setUpdatedAt(now);
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
        evidence.setUpdatedAt(now);
        attempt.setLastTrackingCheckedAt(now);
        attempt.setUpdatedAt(now);

        if (summary.delivered() && attempt.getStatus() != DeliveryAttemptStatus.DELIVERED) {
            attempt.setStatus(DeliveryAttemptStatus.DELIVERED);
            attempt.setDeliveredAt(summary.eventAt() == null ? now : summary.eventAt());
            attempt.setTrackingNextCheckAt(null);
        } else if (!summary.delivered()) {
            attempt.setTrackingNextCheckAt(now.plus(successDelay));
        }
    }

    private void saveTrackingEvents(
            DeliveryAttempt attempt,
            TrackingSummary summary,
            String errorMessage) {
        List<TrackingEvent> events = summary.events().isEmpty()
                ? List.of(new TrackingEvent(
                        TrackingSummary.eventKey(
                                summary.trackingNumber(),
                                summary.statusCode(),
                                summary.status(),
                                summary.eventAt()),
                        summary.trackingNumber(),
                        summary.status(),
                        summary.statusCode(),
                        summary.delivered(),
                        summary.eventAt()))
                : summary.events();

        for (TrackingEvent event : events) {
            if (StringUtils.hasText(event.eventKey())
                    && deliveryTrackingEventRepository.existsByDeliveryAttempt_IdAndEventKey(
                            attempt.getId(),
                            event.eventKey())) {
                continue;
            }
            saveTrackingEvent(attempt, event, errorMessage);
        }
    }

    private void saveTrackingEvent(
            DeliveryAttempt attempt,
            TrackingEvent event,
            String errorMessage) {
        DeliveryTrackingEvent trackingEvent = new DeliveryTrackingEvent();
        trackingEvent.setId(UUID.randomUUID());
        trackingEvent.setDeliveryAttempt(attempt);
        trackingEvent.setTrackingNumber(event.trackingNumber());
        trackingEvent.setEventKey(event.eventKey());
        trackingEvent.setStatus(event.status());
        trackingEvent.setStatusCode(event.statusCode());
        trackingEvent.setDelivered(event.delivered());
        trackingEvent.setEventAt(event.eventAt());
        trackingEvent.setCheckedAt(Instant.now(clock));
        trackingEvent.setErrorMessage(errorMessage);
        deliveryTrackingEventRepository.save(trackingEvent);
    }

    private void saveTrackingEvent(
            DeliveryAttempt attempt,
            String trackingNumber,
            String errorMessage) {
        DeliveryTrackingEvent trackingEvent = new DeliveryTrackingEvent();
        trackingEvent.setId(UUID.randomUUID());
        trackingEvent.setDeliveryAttempt(attempt);
        trackingEvent.setTrackingNumber(trackingNumber);
        trackingEvent.setDelivered(false);
        trackingEvent.setCheckedAt(Instant.now(clock));
        trackingEvent.setErrorMessage(errorMessage);
        deliveryTrackingEventRepository.save(trackingEvent);
    }

    private Duration delayFor(RuntimeException ex) {
        if (ex instanceof TrackingProviderException providerException) {
            TrackingProviderErrorType type = providerException.getErrorType();
            if (type == TrackingProviderErrorType.RATE_LIMITED) {
                return rateLimitDelay;
            }
            if (type == TrackingProviderErrorType.TRANSIENT) {
                return transientFailureDelay;
            }
            return permanentFailureDelay;
        }
        return transientFailureDelay;
    }

    private String outcomeFor(RuntimeException ex) {
        if (ex instanceof TrackingProviderException providerException) {
            return switch (providerException.getErrorType()) {
                case AUTHENTICATION -> "auth_failure";
                case RATE_LIMITED -> "rate_limited";
                case TRANSIENT -> "transient_failure";
                case CONFIGURATION, NOT_FOUND, PARSE_ERROR, PERMANENT -> "permanent_failure";
            };
        }
        return "transient_failure";
    }

    private void recordOutcome(String outcome, String carrierCode, UUID attemptId, RuntimeException ex) {
        meterRegistry.ifPresent(registry -> registry.counter(
                "leasetrack.tracking.sync.outcomes",
                "outcome", outcome,
                "carrier", carrierCode == null ? "unknown" : carrierCode)
                .increment());

        if (ex instanceof TrackingProviderException providerException) {
            LOGGER.info(
                    "tracking_sync outcome={} carrierCode={} attemptId={} providerErrorType={} httpStatus={}",
                    outcome,
                    carrierCode,
                    attemptId,
                    providerException.getErrorType(),
                    providerException.getStatusCode());
            return;
        }

        LOGGER.info(
                "tracking_sync outcome={} carrierCode={} attemptId={} trackingSyncStatus={}",
                outcome,
                carrierCode,
                attemptId,
                statusForOutcome(outcome));
    }

    private TrackingSyncStatus statusForOutcome(String outcome) {
        if (outcome.equals("delivered") || outcome.equals("success") || outcome.equals("certificate_stored")) {
            return TrackingSyncStatus.SUCCESS;
        }
        if (outcome.equals("unsupported_carrier")) {
            return TrackingSyncStatus.NOT_APPLICABLE;
        }
        return TrackingSyncStatus.FAILED;
    }

    private String errorMessage(RuntimeException ex) {
        if (ex instanceof TrackingProviderException providerException && providerException.getStatusCode() != null) {
            return providerException.getErrorType() + " HTTP " + providerException.getStatusCode()
                    + ": " + providerException.getMessage();
        }
        if (ex instanceof TrackingProviderException providerException) {
            return providerException.getErrorType() + ": " + providerException.getMessage();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    private String cap(String value) {
        if (value == null || maxRawPayloadChars <= 0 || value.length() <= maxRawPayloadChars) {
            return value;
        }
        String suffix = "...[truncated]";
        if (maxRawPayloadChars <= suffix.length()) {
            return value.substring(0, maxRawPayloadChars);
        }
        return value.substring(0, maxRawPayloadChars - suffix.length()) + suffix;
    }

    private record TrackingSyncCandidate(
            UUID attemptId,
            String carrierCode,
            String trackingNumber) {
    }
}
