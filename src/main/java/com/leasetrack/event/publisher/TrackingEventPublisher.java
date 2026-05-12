package com.leasetrack.event.publisher;

import com.leasetrack.config.RabbitMqConfig;
import com.leasetrack.event.model.DeliveryConfirmationCertificateRequested;
import com.leasetrack.event.model.TrackingSyncRequested;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrackingEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public TrackingEventPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    public void publishTrackingSyncRequested(UUID deliveryAttemptId, String carrierCode, String trackingNumber) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TRACKING_EXCHANGE,
                RabbitMqConfig.TRACKING_SYNC_ROUTING_KEY,
                new TrackingSyncRequested(
                        UUID.randomUUID(),
                        Instant.now(clock),
                        deliveryAttemptId,
                        carrierCode,
                        trackingNumber,
                        1));
    }

    public void publishDeliveryConfirmationCertificateRequested(
            UUID deliveryAttemptId,
            String carrierCode,
            String trackingNumber) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TRACKING_EXCHANGE,
                RabbitMqConfig.DELIVERY_CONFIRMATION_CERTIFICATE_ROUTING_KEY,
                new DeliveryConfirmationCertificateRequested(
                        UUID.randomUUID(),
                        Instant.now(clock),
                        deliveryAttemptId,
                        carrierCode,
                        trackingNumber,
                        1));
    }
}
