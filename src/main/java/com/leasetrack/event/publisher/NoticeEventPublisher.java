package com.leasetrack.event.publisher;

import com.leasetrack.config.RabbitMqConfig;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.DeliveryEvidence;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.event.model.NoticeEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NoticeEventPublisher {

    private static final String EVENT_VERSION = "1";

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public NoticeEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    public void publishNoticeCreated(Notice notice) {
        UUID attemptId = notice.getDeliveryAttempts().isEmpty()
                ? null
                : notice.getDeliveryAttempts().getFirst().getId();
        publish(new NoticeEvent(
                UUID.randomUUID(),
                "NoticeCreated",
                EVENT_VERSION,
                Instant.now(clock),
                notice.getId(),
                attemptId,
                Map.of(
                        "noticeType", notice.getNoticeType().name(),
                        "status", notice.getStatus().name())));
    }

    public void publishDeliveryStatusUpdated(
            DeliveryAttempt attempt,
            DeliveryAttemptStatus previousStatus,
            DeliveryAttemptStatus newStatus) {
        publish(new NoticeEvent(
                UUID.randomUUID(),
                "DeliveryStatusUpdated",
                EVENT_VERSION,
                Instant.now(clock),
                attempt.getNotice().getId(),
                attempt.getId(),
                Map.of(
                        "previousStatus", previousStatus.name(),
                        "newStatus", newStatus.name())));
    }

    public void publishEvidenceUploaded(DeliveryEvidence evidence, EvidenceStrength evidenceStrength) {
        publish(new NoticeEvent(
                UUID.randomUUID(),
                "EvidenceUploaded",
                EVENT_VERSION,
                Instant.now(clock),
                evidence.getDeliveryAttempt().getNotice().getId(),
                evidence.getDeliveryAttempt().getId(),
                Map.of(
                        "evidenceId", evidence.getId().toString(),
                        "evidenceStrength", evidenceStrength.name())));
    }

    private void publish(NoticeEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.NOTICE_EVENTS_EXCHANGE,
                RabbitMqConfig.NOTICE_EVENTS_ROUTING_KEY,
                event);
    }
}
