package com.leasetrack.controller;

import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.EvidenceDocumentType;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.dto.request.CreateNoticeRequest;
import com.leasetrack.dto.request.UpdateDeliveryAttemptStatusRequest;
import com.leasetrack.dto.request.UpsertDeliveryEvidenceRequest;
import com.leasetrack.dto.response.AuditEventResponse;
import com.leasetrack.dto.response.EvidenceDocumentResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
import com.leasetrack.service.EvidencePackagePdfService;
import com.leasetrack.service.EvidenceDocumentService;
import com.leasetrack.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/notices")
@Tag(name = "Notices")
public class NoticeController {

    private final NoticeService noticeService;
    private final EvidenceDocumentService evidenceDocumentService;
    private final EvidencePackagePdfService evidencePackagePdfService;

    public NoticeController(
            NoticeService noticeService,
            EvidenceDocumentService evidenceDocumentService,
            EvidencePackagePdfService evidencePackagePdfService) {
        this.noticeService = noticeService;
        this.evidenceDocumentService = evidenceDocumentService;
        this.evidencePackagePdfService = evidencePackagePdfService;
    }

    @PostMapping
    @Operation(summary = "Create a notice with an initial delivery attempt")
    public ResponseEntity<NoticeResponse> createNotice(@Valid @RequestBody CreateNoticeRequest request) {
        NoticeResponse response = noticeService.createNotice(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a notice by id")
    public NoticeResponse getNotice(@PathVariable UUID id) {
        return noticeService.getNotice(id);
    }

    @GetMapping
    @Operation(summary = "List notices visible to the authenticated user")
    public Page<NoticeSummaryResponse> listNotices(
            @RequestParam(required = false) NoticeStatus status,
            @RequestParam(required = false) NoticeType noticeType,
            @RequestParam(required = false) DeliveryMethod deliveryMethod,
            @RequestParam(required = false) UUID leaseId,
            @RequestParam(required = false) Instant deadlineAfter,
            @RequestParam(required = false) Instant deadlineBefore,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return noticeService.listNotices(status, noticeType, deliveryMethod, leaseId, deadlineAfter, deadlineBefore, pageable);
    }

    @PatchMapping("/{noticeId}/attempts/{attemptId}/status")
    @Operation(summary = "Update a delivery attempt status")
    public NoticeResponse updateDeliveryAttemptStatus(
            @PathVariable UUID noticeId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody UpdateDeliveryAttemptStatusRequest request) {
        return noticeService.updateDeliveryAttemptStatus(noticeId, attemptId, request);
    }

    @PostMapping("/{noticeId}/attempts/{attemptId}/evidence")
    @Operation(summary = "Create or update proof-of-delivery evidence")
    public DeliveryEvidenceResponse upsertDeliveryEvidence(
            @PathVariable UUID noticeId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody UpsertDeliveryEvidenceRequest request) {
        return noticeService.upsertDeliveryEvidence(noticeId, attemptId, request);
    }

    @PostMapping(
            value = "/{noticeId}/attempts/{attemptId}/evidence/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a proof-of-delivery evidence document")
    public EvidenceDocumentResponse uploadEvidenceDocument(
            @PathVariable UUID noticeId,
            @PathVariable UUID attemptId,
            @RequestParam EvidenceDocumentType documentType,
            @RequestParam MultipartFile file) {
        return evidenceDocumentService.uploadEvidenceDocument(noticeId, attemptId, documentType, file);
    }

    @GetMapping("/{noticeId}/attempts/{attemptId}/evidence/documents")
    @Operation(summary = "List uploaded evidence document metadata for a delivery attempt")
    public List<EvidenceDocumentResponse> listEvidenceDocuments(
            @PathVariable UUID noticeId,
            @PathVariable UUID attemptId) {
        return evidenceDocumentService.listEvidenceDocuments(noticeId, attemptId);
    }

    @GetMapping("/{noticeId}/audit-log")
    @Operation(summary = "Retrieve the audit log for a notice")
    public List<AuditEventResponse> getAuditLog(@PathVariable UUID noticeId) {
        return noticeService.getAuditLog(noticeId);
    }

    @GetMapping("/{noticeId}/evidence-package")
    @Operation(summary = "Generate a JSON evidence package for a notice")
    public EvidencePackageResponse getEvidencePackage(@PathVariable UUID noticeId) {
        return noticeService.getEvidencePackage(noticeId);
    }

    @GetMapping(value = "/{noticeId}/evidence-package.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate a PDF evidence package for a notice")
    public ResponseEntity<byte[]> getEvidencePackagePdf(@PathVariable UUID noticeId) {
        EvidencePackageResponse evidencePackage = noticeService.getEvidencePackage(noticeId);
        byte[] pdf = evidencePackagePdfService.render(evidencePackage);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"lease-track-evidence-" + noticeId + ".pdf\"")
                .body(pdf);
    }
}
