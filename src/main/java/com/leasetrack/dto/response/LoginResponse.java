package com.leasetrack.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds) {
}
