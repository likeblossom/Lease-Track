package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.ActorRole;
import com.leasetrack.domain.enums.LeaseEventType;
import java.time.Instant;
import java.util.UUID;

public record LeaseEventResponse(
        UUID id,
        UUID leaseId,
        LeaseEventType eventType,
        ActorRole actorRole,
        String actorReference,
        String details,
        Instant createdAt) {
}
