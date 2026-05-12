package com.leasetrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtService implements InitializingBean {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String DEFAULT_LOCAL_SECRET_PREFIX = "local-dev-jwt-secret";

    private final String secret;
    private final long expirationSeconds;
    private final Clock clock;
    private final Environment environment;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds,
            Clock clock,
            Environment environment) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("app.jwt.expiration-seconds must be greater than zero");
        }
        boolean productionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
        if (productionProfile && secret.startsWith(DEFAULT_LOCAL_SECRET_PREFIX)) {
            throw new IllegalStateException("APP_JWT_SECRET must be set to a production secret");
        }
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getUser().getId().toString())
                .claim("role", userPrincipal.getUser().getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserPrincipal userPrincipal) {
        return extractUsername(token).equals(userPrincipal.getUsername())
                && claims(token).getExpiration().after(Date.from(Instant.now(clock)));
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
