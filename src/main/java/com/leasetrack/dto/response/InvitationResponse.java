package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.UserRole;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        Instant expiresAt,
        String token) {
}
