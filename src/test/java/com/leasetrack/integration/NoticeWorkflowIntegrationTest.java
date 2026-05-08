package com.leasetrack.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
    static final RabbitMQContainer RABBITMQ = rabbitMqContainer();

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
        registry.add("app.storage.local.root", () -> "target/test-evidence-documents");
    }

    private static RabbitMQContainer rabbitMqContainer() {
        RabbitMQContainer container = new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:3.13-management-alpine"));
        container.withAdminUser("leasetrack_test");
        container.withAdminPassword("leasetrack_test_password");
        return container;
    }

    @Test
    void createNoticeAddEvidenceAndGenerateEvidencePackage() {
        register("admin@leasetrack.test", "Admin User", "ADMIN");
        register("landlord@leasetrack.test", "Landlord User", "LANDLORD");
        String adminToken = login("admin@leasetrack.test");
        String landlordToken = login("landlord@leasetrack.test");
        UUID tenantUserId = acceptInvitation(
                createInvitation(adminToken, "tenant@leasetrack.test", "Tenant User", "TENANT"),
                "Tenant User");
        String otherTenantToken = loginAcceptedTenant(
                adminToken,
                "other-tenant@leasetrack.test",
                "Other Tenant");

        ResponseEntity<JsonNode> createNoticeResponse = restTemplate.exchange(
                url("/api/notices"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "recipientName", "Marie Tremblay",
                        "recipientContactInfo", "marie@example.com",
                        "noticeType", "RENT_INCREASE",
                        "deliveryMethod", "REGISTERED_MAIL",
                        "tenantUserId", tenantUserId.toString(),
                        "deadlineAt", "2026-06-01T12:00:00Z",
                        "notes", "Integration test notice"), authHeaders(landlordToken)),
                JsonNode.class);

        assertThat(createNoticeResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode notice = createNoticeResponse.getBody();
        assertThat(notice).isNotNull();
        UUID noticeId = UUID.fromString(notice.get("id").asText());
        UUID attemptId = UUID.fromString(notice.get("deliveryAttempts").get(0).get("id").asText());

        ResponseEntity<JsonNode> tenantNoticeResponse = restTemplate.exchange(
                url("/api/notices/%s".formatted(noticeId)),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login("tenant@leasetrack.test"))),
                JsonNode.class);

        assertThat(tenantNoticeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tenantNoticeResponse.getBody()).isNotNull();
        assertThat(tenantNoticeResponse.getBody().get("id").asText()).isEqualTo(noticeId.toString());

        ResponseEntity<JsonNode> otherTenantNoticeResponse = restTemplate.exchange(
                url("/api/notices/%s".formatted(noticeId)),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherTenantToken)),
                JsonNode.class);

        assertThat(otherTenantNoticeResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> evidenceResponse = restTemplate.exchange(
                url("/api/notices/%s/attempts/%s/evidence".formatted(noticeId, attemptId)),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "trackingNumber", "RN123456789CA",
                        "carrierName", "Canada Post",
                        "carrierReceiptRef", "receipts/rn123456789ca.pdf",
                        "deliveryConfirmation", true,
                        "deliveryConfirmationMetadata", "Delivered to recipient"), authHeaders(adminToken)),
                JsonNode.class);

        assertThat(evidenceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evidenceResponse.getBody()).isNotNull();
        assertThat(evidenceResponse.getBody().get("evidenceStrength").asText()).isEqualTo("STRONG");

        ResponseEntity<JsonNode> documentResponse = restTemplate.exchange(
                url("/api/notices/%s/attempts/%s/evidence/documents?documentType=CARRIER_RECEIPT"
                        .formatted(noticeId, attemptId)),
                HttpMethod.POST,
                multipartEntity(adminToken, "carrier-receipt.pdf", "receipt".getBytes()),
                JsonNode.class);

        assertThat(documentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documentResponse.getBody()).isNotNull();
        assertThat(documentResponse.getBody().get("documentType").asText()).isEqualTo("CARRIER_RECEIPT");

        ResponseEntity<JsonNode> packageResponse = restTemplate.exchange(
                url("/api/notices/%s/evidence-package".formatted(noticeId)),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                JsonNode.class);

        assertThat(packageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(packageResponse.getBody()).isNotNull();
        assertThat(packageResponse.getBody().get("noticeId").asText()).isEqualTo(noticeId.toString());
        assertThat(packageResponse.getBody().get("strongestEvidenceStrength").asText()).isEqualTo("STRONG");
        assertThat(packageResponse.getBody().get("evidenceDocuments").size()).isEqualTo(1);
        assertThat(packageResponse.getBody().get("auditEvents").size()).isGreaterThanOrEqualTo(3);
    }

    private void register(String email, String displayName, String role) {
        ResponseEntity<JsonNode> registerResponse = restTemplate.exchange(
                url("/api/auth/register"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", email,
                        "password", "password",
                        "displayName", displayName,
                        "role", role), jsonHeaders()),
                JsonNode.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String createInvitation(String inviterToken, String email, String displayName, String role) {
        ResponseEntity<JsonNode> invitationResponse = restTemplate.exchange(
                url("/api/auth/invitations"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", email,
                        "displayName", displayName,
                        "role", role), authHeaders(inviterToken)),
                JsonNode.class);

        assertThat(invitationResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(invitationResponse.getBody()).isNotNull();
        return invitationResponse.getBody().get("token").asText();
    }

    private UUID acceptInvitation(String token, String displayName) {
        ResponseEntity<JsonNode> acceptResponse = restTemplate.exchange(
                url("/api/auth/invitations/accept"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "token", token,
                        "password", "password",
                        "displayName", displayName), jsonHeaders()),
                JsonNode.class);

        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(acceptResponse.getBody()).isNotNull();
        return UUID.fromString(acceptResponse.getBody().get("id").asText());
    }

    private String loginAcceptedTenant(String adminToken, String email, String displayName) {
        acceptInvitation(createInvitation(adminToken, email, displayName, "TENANT"), displayName);
        return login(email);
    }

    private String login(String email) {
        HttpHeaders headers = jsonHeaders();
        ResponseEntity<JsonNode> loginResponse = restTemplate.exchange(
                url("/api/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", email,
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

    private HttpEntity<MultiValueMap<String, Object>> multipartEntity(
            String token,
            String filename,
            byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        return new HttpEntity<>(body, headers);
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
