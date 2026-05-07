package com.leasetrack.event.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NoticeEvent(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID noticeId,
        UUID deliveryAttemptId,
        Map<String, String> payload) {
}
