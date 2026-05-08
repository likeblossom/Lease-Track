package com.leasetrack.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NoticeWorkflowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("app.jwt.secret", () -> "integration-test-jwt-secret-change-before-use-32-bytes-min");
        registry.add("app.schedulers.deadline.fixed-delay-ms", () -> "3600000");
        registry.add("app.schedulers.tracking.fixed-delay-ms", () -> "3600000");
    }

    @Test
    void createNoticeAddEvidenceAndGenerateEvidencePackage() {
        String token = loginAsLandlord();
        HttpHeaders authHeaders = authHeaders(token);

        ResponseEntity<JsonNode> createNoticeResponse = restTemplate.exchange(
                url("/api/notices"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "recipientName", "Marie Tremblay",
                        "recipientContactInfo", "marie@example.com",
                        "noticeType", "RENT_INCREASE",
                        "deliveryMethod", "REGISTERED_MAIL",
                        "deadlineAt", "2026-06-01T12:00:00Z",
                        "notes", "Integration test notice"), authHeaders),
                JsonNode.class);

        assertThat(createNoticeResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode notice = createNoticeResponse.getBody();
        assertThat(notice).isNotNull();
        UUID noticeId = UUID.fromString(notice.get("id").asText());
        UUID attemptId = UUID.fromString(notice.get("deliveryAttempts").get(0).get("id").asText());

        ResponseEntity<JsonNode> evidenceResponse = restTemplate.exchange(
                url("/api/notices/%s/attempts/%s/evidence".formatted(noticeId, attemptId)),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "trackingNumber", "RN123456789CA",
                        "carrierName", "Canada Post",
                        "carrierReceiptRef", "receipts/rn123456789ca.pdf",
                        "deliveryConfirmation", true,
                        "deliveryConfirmationMetadata", "Delivered to recipient"), authHeaders),
                JsonNode.class);

        assertThat(evidenceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evidenceResponse.getBody()).isNotNull();
        assertThat(evidenceResponse.getBody().get("evidenceStrength").asText()).isEqualTo("STRONG");

        ResponseEntity<JsonNode> packageResponse = restTemplate.exchange(
                url("/api/notices/%s/evidence-package".formatted(noticeId)),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                JsonNode.class);

        assertThat(packageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(packageResponse.getBody()).isNotNull();
        assertThat(packageResponse.getBody().get("noticeId").asText()).isEqualTo(noticeId.toString());
        assertThat(packageResponse.getBody().get("strongestEvidenceStrength").asText()).isEqualTo("STRONG");
        assertThat(packageResponse.getBody().get("auditEvents").size()).isGreaterThanOrEqualTo(3);
    }

    private String loginAsLandlord() {
        HttpHeaders headers = jsonHeaders();
        ResponseEntity<JsonNode> loginResponse = restTemplate.exchange(
                url("/api/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", "landlord@leasetrack.dev",
                        "password", "password"), headers),
                JsonNode.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        return loginResponse.getBody().get("accessToken").asText();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:%d%s".formatted(port, path);
    }
}
