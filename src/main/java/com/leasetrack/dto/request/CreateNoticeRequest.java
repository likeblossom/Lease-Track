package com.leasetrack.dto.request;

import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.NoticeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateNoticeRequest(
        @NotBlank String recipientName,
        @NotBlank String recipientContactInfo,
        @NotNull NoticeType noticeType,
        @NotNull DeliveryMethod deliveryMethod,
        UUID leaseId,
        UUID tenantUserId,
        Instant deadlineAt,
        String notes) {
}
