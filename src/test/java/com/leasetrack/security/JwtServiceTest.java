package com.leasetrack.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-12T10:00:00Z"), ZoneOffset.UTC);
    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsShortSigningSecret() {
        JwtService jwtService = new JwtService("too-short", 3600, CLOCK, new MockEnvironment());

        assertThatThrownBy(jwtService::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_JWT_SECRET must be at least 32 bytes");
    }

    @Test
    void rejectsDefaultSigningSecretInProduction() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");
        JwtService jwtService = new JwtService(
                "local-dev-jwt-secret-change-before-use-32-bytes-min",
                3600,
                CLOCK,
                environment);

        assertThatThrownBy(jwtService::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_JWT_SECRET must be set to a production secret");
    }

    @Test
    void acceptsStrongSigningSecret() {
        JwtService jwtService = new JwtService(VALID_SECRET, 3600, CLOCK, new MockEnvironment());

        assertThatCode(jwtService::afterPropertiesSet).doesNotThrowAnyException();
    }
}
