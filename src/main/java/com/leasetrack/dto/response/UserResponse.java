package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.UserRole;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role) {
}
