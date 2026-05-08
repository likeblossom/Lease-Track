package com.leasetrack.dto.request;

import com.leasetrack.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 128) String password,
        @NotBlank String displayName,
        @NotNull UserRole role) {
}
