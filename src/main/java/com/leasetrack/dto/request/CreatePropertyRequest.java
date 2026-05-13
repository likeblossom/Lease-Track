package com.leasetrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreatePropertyRequest(
        @NotBlank String name,
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String city,
        @NotBlank String province,
        @NotBlank
        @Pattern(regexp = "^[A-Z]\\d[A-Z] \\d[A-Z]\\d$", message = "Postal code must use format A1A 1A1")
        String postalCode,
        String country,
        String notes) {
}
