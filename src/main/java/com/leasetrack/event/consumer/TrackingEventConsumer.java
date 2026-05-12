package com.leasetrack.event.consumer;

import com.leasetrack.config.RabbitMqConfig;
import com.leasetrack.event.model.DeliveryConfirmationCertificateRequested;
import com.leasetrack.event.model.TrackingSyncRequested;
import com.leasetrack.service.TrackingSyncService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TrackingEventConsumer {

    private final TrackingSyncService trackingSyncService;

    public TrackingEventConsumer(TrackingSyncService trackingSyncService) {
        this.trackingSyncService = trackingSyncService;
    }

    @RabbitListener(queues = RabbitMqConfig.TRACKING_SYNC_QUEUE)
    public void consumeTrackingSyncRequested(TrackingSyncRequested event) {
        trackingSyncService.processTrackingSync(event);
    }

    @RabbitListener(queues = RabbitMqConfig.DELIVERY_CONFIRMATION_CERTIFICATE_QUEUE)
    public void consumeDeliveryConfirmationCertificateRequested(DeliveryConfirmationCertificateRequested event) {
        trackingSyncService.processDeliveryConfirmationCertificate(event);
    }
}
