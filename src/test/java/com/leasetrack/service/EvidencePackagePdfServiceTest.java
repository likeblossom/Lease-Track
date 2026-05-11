package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.leasetrack.domain.enums.ActorRole;
import com.leasetrack.domain.enums.AuditEventType;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceDocumentType;
import com.leasetrack.domain.enums.EvidenceStrength;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.domain.enums.TrackingSyncStatus;
import com.leasetrack.dto.response.AuditEventResponse;
import com.leasetrack.dto.response.DeliveryAttemptResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.dto.response.EvidencePackageAttemptResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.TrackingEventResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class EvidencePackagePdfServiceTest {

    private final EvidencePackagePdfService service = new EvidencePackagePdfService();

    @Test
    void rendersEvidencePackagePdfWithCoreAuditAndTrackingContent() throws Exception {
        Instant now = Instant.parse("2026-05-08T12:00:00Z");
        UUID noticeId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DeliveryAttemptResponse attempt = new DeliveryAttemptResponse(
                attemptId,
                1,
                DeliveryMethod.REGISTERED_MAIL,
                DeliveryAttemptStatus.DELIVERED,
                now.minusSeconds(7200),
                now,
                now.plusSeconds(86400),
                TrackingSyncStatus.SUCCESS,
                now,
                false,
                now.minusSeconds(9000),
                now);
        DeliveryEvidenceResponse evidence = new DeliveryEvidenceResponse(
                evidenceId,
                attemptId,
                "123456789012",
                "FedEx",
                "fedex",
                "receipt-1",
                true,
                "{\"provider\":\"fedex\"}",
                null,
                null,
                null,
                null,
                "Delivered",
                "DL",
                now,
                null,
                EvidenceStrength.STRONG,
                now.minusSeconds(8000),
                now);
        EvidenceDocumentResponse document = new EvidenceDocumentResponse(
                documentId,
                noticeId,
                attemptId,
                evidenceId,
                EvidenceDocumentType.CARRIER_RECEIPT,
                "receipt.pdf",
                "application/pdf",
                2048,
                "s3",
                "prod/evidence/receipt.pdf",
                "abc123",
                userId,
                now);
        TrackingEventResponse trackingEvent = new TrackingEventResponse(
                UUID.randomUUID(),
                attemptId,
                "123456789012",
                "fedex:123456789012:DL:2026-05-08T12:00:00Z",
                "Delivered",
                "DL",
                true,
                now,
                now,
                null);
        AuditEventResponse auditEvent = new AuditEventResponse(
                UUID.randomUUID(),
                noticeId,
                attemptId,
                AuditEventType.EVIDENCE_PACKAGE_GENERATED,
                ActorRole.LANDLORD,
                "landlord@leasetrack.dev",
                "Generated evidence package",
                now);
        NoticeResponse notice = new NoticeResponse(
                noticeId,
                "Marie Tremblay",
                "marie@example.com",
                NoticeType.RENT_INCREASE,
                NoticeStatus.OPEN,
                userId,
                null,
                "Initial notice",
                now.minusSeconds(10000),
                now,
                null,
                List.of(attempt));
        EvidencePackageResponse evidencePackage = new EvidencePackageResponse(
                UUID.randomUUID(),
                "1.0",
                "package-hash-123",
                noticeId,
                userId,
                now,
                notice,
                List.of(new EvidencePackageAttemptResponse(attempt, evidence, List.of(document), List.of(trackingEvent))),
                List.of(evidence),
                List.of(document),
                List.of(trackingEvent),
                List.of(auditEvent),
                EvidenceStrength.STRONG);

        byte[] pdf = service.render(evidencePackage);

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PDDocument loaded = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(loaded);
            assertThat(text).contains("Lease-Track Evidence Package");
            assertThat(text).contains("Package hash: package-hash-123");
            assertThat(text).contains("Recipient: Marie Tremblay");
            assertThat(text).contains("Carrier: FedEx (fedex)");
            assertThat(text).contains("Tracking number: 123456789012");
            assertThat(text).contains("Delivered");
            assertThat(text).contains("receipt.pdf");
            assertThat(text).contains("EVIDENCE_PACKAGE_GENERATED");
            assertThat(text).contains("Generated");
            assertThat(text).contains("evidence package");
        }
    }
}
