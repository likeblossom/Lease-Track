package com.leasetrack.controller;

import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.dto.request.CreateNoticeRequest;
import com.leasetrack.dto.request.UpdateDeliveryAttemptStatusRequest;
import com.leasetrack.dto.request.UpsertDeliveryEvidenceRequest;
import com.leasetrack.dto.response.AuditEventResponse;
import com.leasetrack.dto.response.DeliveryEvidenceResponse;
import com.leasetrack.dto.response.EvidencePackageResponse;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.dto.response.NoticeSummaryResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/notices")
@Tag(name = "Notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
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
            @RequestParam(required = false) Instant deadlineAfter,
            @RequestParam(required = false) Instant deadlineBefore,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return noticeService.listNotices(status, noticeType, deliveryMethod, deadlineAfter, deadlineBefore, pageable);
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
}
