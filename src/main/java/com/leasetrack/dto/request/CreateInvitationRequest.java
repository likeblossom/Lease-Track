package com.leasetrack.dto.request;

import com.leasetrack.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvitationRequest(
        @Email @NotBlank String email,
        String displayName,
        @NotNull UserRole role) {
}
