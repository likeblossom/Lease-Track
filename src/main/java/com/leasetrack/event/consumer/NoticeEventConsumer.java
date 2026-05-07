package com.leasetrack.event.consumer;

import com.leasetrack.config.RabbitMqConfig;
import com.leasetrack.event.model.NoticeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NoticeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NoticeEventConsumer.class);

    @RabbitListener(queues = RabbitMqConfig.NOTICE_EVENTS_QUEUE)
    public void consume(NoticeEvent event) {
        log.info(
                "Received notice event: eventType={}, noticeId={}, deliveryAttemptId={}",
                event.eventType(),
                event.noticeId(),
                event.deliveryAttemptId());
        log.info("Simulated notification for eventId={}", event.eventId());
    }
}
