package com.leasetrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @Size(min = 8, max = 128) String password,
        String displayName) {
}
