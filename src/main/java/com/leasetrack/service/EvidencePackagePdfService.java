package com.leasetrack.service;

import com.leasetrack.dto.response.AuditEventResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.dto.response.EvidencePackageAttemptResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.TrackingEventResponse;
import com.leasetrack.exception.EvidencePackageGenerationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvidencePackagePdfService {

    private static final float MARGIN = 50;
    private static final float LABEL_WIDTH = 160;
    private static final float LINE_HEIGHT = 16;
    private static final int BODY_WRAP_CHARS = 82;
    private static final int VALUE_WRAP_CHARS = 58;
    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a 'UTC'").withZone(ZoneId.of("UTC"));
    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] render(EvidencePackageResponse evidencePackage) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(document);

            writeCover(writer, evidencePackage);
            writeNoticeSummary(writer, evidencePackage.notice());
            writeDeliveryAttempts(writer, evidencePackage);
            writeDocumentIndex(writer, list(evidencePackage.evidenceDocuments()));
            writeTrackingSummary(writer, list(evidencePackage.trackingHistory()));
            writeAuditLog(writer, list(evidencePackage.auditEvents()));

            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new EvidencePackageGenerationException("Unable to render evidence package PDF", ex);
        }
    }

    private void writeCover(PdfWriter writer, EvidencePackageResponse evidencePackage) throws IOException {
        writer.title("Notice Delivery Evidence Report");
        writer.subtitle("Prepared by Lease Track for property management review");
        writer.rule();
        writer.section("Summary");
        writer.keyValue("Recipient", evidencePackage.notice().recipientName());
        writer.keyValue("Notice type", label(evidencePackage.notice().noticeType()));
        writer.keyValue("Notice status", label(evidencePackage.notice().status()));
        writer.keyValue("Evidence level", evidenceLevel(evidencePackage));
        writer.keyValue("Report generated", format(evidencePackage.generatedAt()));
        writer.blank();
        writer.callout("What this report includes",
                "A plain-language summary of the notice, delivery attempts, tracking information, uploaded proof documents, and recorded activity.");
    }

    private void writeNoticeSummary(PdfWriter writer, NoticeResponse notice) throws IOException {
        writer.section("Notice Details");
        writer.keyValue("Recipient", notice.recipientName());
        writer.keyValue("Recipient contact", notice.recipientContactInfo());
        writer.keyValue("Notice type", label(notice.noticeType()));
        writer.keyValue("Current status", label(notice.status()));
        writer.keyValue("Notice created", format(notice.createdAt()));
        writer.keyValue("Last updated", format(notice.updatedAt()));
        writer.keyValue("Notes", notice.notes());
    }

    private void writeDeliveryAttempts(PdfWriter writer, EvidencePackageResponse evidencePackage) throws IOException {
        writer.section("Delivery Attempts");
        List<EvidencePackageAttemptResponse> attempts = list(evidencePackage.attempts());
        if (attempts.isEmpty()) {
            writer.line("No delivery attempts recorded.");
            return;
        }

        for (EvidencePackageAttemptResponse attemptPackage : attempts) {
            var attempt = attemptPackage.attempt();
            writer.subsection("Attempt " + attempt.attemptNumber() + " - " + label(attempt.deliveryMethod()));
            writer.keyValue("Delivery status", label(attempt.status()));
            writer.keyValue("Sent at", format(attempt.sentAt()));
            writer.keyValue("Delivered at", format(attempt.deliveredAt()));
            writer.keyValue("Response deadline", format(attempt.deadlineAt()));
            writer.keyValue("Last tracking check", format(attempt.lastTrackingCheckedAt()));
            writeEvidence(writer, attemptPackage.evidence());
            writeDocumentsForAttempt(writer, list(attemptPackage.evidenceDocuments()));
            writeTrackingForAttempt(writer, list(attemptPackage.trackingHistory()));
            writer.blank();
        }
    }

    private void writeEvidence(PdfWriter writer, DeliveryEvidenceResponse evidence) throws IOException {
        writer.minorHeading("Proof provided");
        if (evidence == null) {
            writer.line("No proof-of-delivery evidence recorded for this attempt.");
            return;
        }
        writer.keyValue("Evidence level", evidenceLevel(evidence.evidenceStrength()));
        writer.keyValue("Carrier", carrier(evidence));
        writer.keyValue("Tracking number", evidence.trackingNumber());
        writer.keyValue("Carrier receipt reference", evidence.carrierReceiptRef());
        writer.keyValue("Delivery confirmed", yesNo(evidence.deliveryConfirmation()));
        writer.keyValue("Confirmation details", evidence.deliveryConfirmationMetadata());
        writer.keyValue("Signed acknowledgement", evidence.signedAcknowledgementRef());
        writer.keyValue("Email acknowledgement", evidence.emailAcknowledgementRef());
        writer.keyValue("Email acknowledgement details", evidence.emailAcknowledgementMetadata());
        writer.keyValue("Bailiff affidavit", evidence.bailiffAffidavitRef());
        writer.keyValue("Latest carrier status", evidence.latestTrackingStatus());
        writer.keyValue("Latest carrier event", format(evidence.latestTrackingEventAt()));
    }

    private void writeDocumentsForAttempt(PdfWriter writer, List<EvidenceDocumentResponse> documents) throws IOException {
        writer.minorHeading("Uploaded documents for this attempt");
        if (documents.isEmpty()) {
            writer.line("No evidence documents uploaded for this attempt.");
            return;
        }
        for (EvidenceDocumentResponse document : documents) {
            writeDocument(writer, document);
        }
    }

    private void writeTrackingForAttempt(PdfWriter writer, List<TrackingEventResponse> trackingHistory) throws IOException {
        writer.minorHeading("Carrier tracking history for this attempt");
        if (trackingHistory.isEmpty()) {
            writer.line("No carrier tracking history recorded for this attempt.");
            return;
        }
        for (TrackingEventResponse event : trackingHistory) {
            writer.bullet(format(event.eventAt())
                    + " | " + value(event.status())
                    + " | Delivered: " + yesNo(event.delivered()));
        }
    }

    private void writeDocumentIndex(PdfWriter writer, List<EvidenceDocumentResponse> documents) throws IOException {
        writer.section("Uploaded Documents");
        if (documents.isEmpty()) {
            writer.line("No evidence documents uploaded.");
            return;
        }
        for (EvidenceDocumentResponse document : documents) {
            writeDocument(writer, document);
        }
    }

    private void writeDocument(PdfWriter writer, EvidenceDocumentResponse document) throws IOException {
        writer.bullet(label(document.documentType()) + " - " + value(document.originalFilename()));
        writer.keyValue("Content type", document.contentType());
        writer.keyValue("File size", formatBytes(document.sizeBytes()));
        writer.keyValue("Uploaded at", format(document.createdAt()));
    }

    private void writeTrackingSummary(PdfWriter writer, List<TrackingEventResponse> trackingHistory) throws IOException {
        writer.section("Tracking History");
        if (trackingHistory.isEmpty()) {
            writer.line("No tracking history recorded.");
            return;
        }
        for (TrackingEventResponse event : trackingHistory) {
            writer.bullet(format(event.eventAt())
                    + " | " + value(event.status())
                    + " | tracking " + value(event.trackingNumber())
                    + " | Delivered: " + yesNo(event.delivered()));
        }
    }

    private void writeAuditLog(PdfWriter writer, List<AuditEventResponse> auditEvents) throws IOException {
        writer.section("Activity History");
        if (auditEvents.isEmpty()) {
            writer.line("No audit events recorded.");
            return;
        }
        for (AuditEventResponse auditEvent : auditEvents) {
            writer.bullet(format(auditEvent.createdAt()) + " | " + label(auditEvent.eventType())
                    + " | " + label(auditEvent.actorRole()));
            writer.keyValue("Recorded by", auditEvent.actorReference());
            writer.keyValue("Details", auditEvent.details());
        }
    }

    private static String carrier(DeliveryEvidenceResponse evidence) {
        String carrierName = value(evidence.carrierName());
        return "not recorded".equals(carrierName) ? value(evidence.carrierCode()) : carrierName;
    }

    private static String evidenceLevel(EvidencePackageResponse evidencePackage) {
        return evidenceLevel(evidencePackage.strongestEvidenceStrength());
    }

    private static String evidenceLevel(Object strength) {
        String display = label(strength);
        if ("Strong".equals(display)) {
            return "Strong - delivery proof is well supported";
        }
        if ("Medium".equals(display)) {
            return "Medium - delivery proof is partially supported";
        }
        if ("Weak".equals(display)) {
            return "Weak - more delivery proof may be needed";
        }
        return display;
    }

    private static String yesNo(Boolean value) {
        if (value == null) {
            return "not recorded";
        }
        return value ? "Yes" : "No";
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String formatBytes(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " bytes";
        }
        if (sizeBytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    private static String format(Instant instant) {
        return instant == null ? "not recorded" : INSTANT_FORMATTER.format(instant);
    }

    private static String value(Object value) {
        if (value == null) {
            return "not recorded";
        }
        String text = Objects.toString(value);
        return StringUtils.hasText(text) ? text : "not recorded";
    }

    private static String label(Object value) {
        return value(value)
                .replace('-', '_')
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .transform(text -> {
                    StringBuilder builder = new StringBuilder();
                    for (String part : text.split(" ")) {
                        if (part.isBlank()) {
                            continue;
                        }
                        if (!builder.isEmpty()) {
                            builder.append(' ');
                        }
                        builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                    }
                    return builder.toString();
                });
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class PdfWriter {

        private final PDDocument document;
        private PDPageContentStream contentStream;
        private float y;

        private PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            addPage();
        }

        private void title(String text) throws IOException {
            ensureSpace(LINE_HEIGHT * 3);
            write(text, BOLD_FONT, 20);
        }

        private void subtitle(String text) throws IOException {
            writeWrapped(text, FONT, 11, BODY_WRAP_CHARS, MARGIN);
            blank();
        }

        private void section(String text) throws IOException {
            ensureSpace(LINE_HEIGHT * 3);
            blank();
            write(text, BOLD_FONT, 15);
            rule();
        }

        private void subsection(String text) throws IOException {
            ensureSpace(LINE_HEIGHT * 3);
            blank();
            write(text, BOLD_FONT, 13);
        }

        private void minorHeading(String text) throws IOException {
            ensureSpace(LINE_HEIGHT * 2);
            write(text, BOLD_FONT, 11);
        }

        private void callout(String label, String text) throws IOException {
            keyValue(label, text);
        }

        private void keyValue(String label, Object rawValue) throws IOException {
            String normalizedLabel = safeText(label) + ":";
            List<String> wrappedValues = wrap(safeText(value(rawValue)), VALUE_WRAP_CHARS);
            ensureSpace(Math.max(1, wrappedValues.size()) * LINE_HEIGHT);
            writeAt(normalizedLabel, BOLD_FONT, 10, MARGIN);
            writeAt(wrappedValues.get(0), FONT, 10, MARGIN + LABEL_WIDTH);
            y -= LINE_HEIGHT;
            for (int index = 1; index < wrappedValues.size(); index++) {
                ensureSpace(LINE_HEIGHT);
                writeAt(wrappedValues.get(index), FONT, 10, MARGIN + LABEL_WIDTH);
                y -= LINE_HEIGHT;
            }
        }

        private void bullet(String text) throws IOException {
            List<String> wrappedLines = wrap(safeText(text), BODY_WRAP_CHARS);
            for (int index = 0; index < wrappedLines.size(); index++) {
                ensureSpace(LINE_HEIGHT);
                String prefix = index == 0 ? "- " : "  ";
                writeAt(prefix + wrappedLines.get(index), FONT, 10, MARGIN + 10);
                y -= LINE_HEIGHT;
            }
        }

        private void line(String text) throws IOException {
            writeWrapped(text, FONT, 10, BODY_WRAP_CHARS, MARGIN);
        }

        private void blank() throws IOException {
            ensureSpace(LINE_HEIGHT);
            y -= LINE_HEIGHT / 2;
        }

        private void rule() throws IOException {
            ensureSpace(8);
            contentStream.moveTo(MARGIN, y);
            contentStream.lineTo(PDRectangle.LETTER.getWidth() - MARGIN, y);
            contentStream.stroke();
            y -= 10;
        }

        private void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
        }

        private void writeWrapped(String text, PDType1Font font, int fontSize, int maxChars, float x) throws IOException {
            for (String wrappedLine : wrap(safeText(text), maxChars)) {
                ensureSpace(LINE_HEIGHT);
                writeAt(wrappedLine, font, fontSize, x);
                y -= LINE_HEIGHT;
            }
        }

        private void write(String text, PDType1Font font, int fontSize) throws IOException {
            ensureSpace(LINE_HEIGHT);
            writeAt(safeText(text), font, fontSize, MARGIN);
            y -= LINE_HEIGHT;
        }

        private void writeAt(String text, PDType1Font font, int fontSize, float x) throws IOException {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(text);
            contentStream.endText();
        }

        private void ensureSpace(float requiredSpace) throws IOException {
            if (y - requiredSpace < MARGIN) {
                addPage();
            }
        }

        private void addPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private List<String> wrap(String text, int maxChars) {
            List<String> lines = new ArrayList<>();
            String remaining = text;
            while (remaining.length() > maxChars) {
                int splitAt = remaining.lastIndexOf(' ', maxChars);
                if (splitAt <= 0) {
                    splitAt = maxChars;
                }
                lines.add(remaining.substring(0, splitAt).trim());
                remaining = remaining.substring(splitAt).trim();
            }
            lines.add(remaining);
            return lines;
        }

        private String safeText(String text) {
            return text == null
                    ? ""
                    : text.replaceAll("\\p{Cntrl}", " ")
                            .replaceAll("[^\\p{Print}]", "?");
        }
    }
}
