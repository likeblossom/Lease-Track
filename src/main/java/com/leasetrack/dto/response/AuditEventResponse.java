package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.ActorRole;
import com.leasetrack.domain.enums.AuditEventType;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID noticeId,
        UUID deliveryAttemptId,
        AuditEventType eventType,
        ActorRole actorRole,
        String actorReference,
        String details,
        Instant createdAt) {
}
