package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoticeResponse(
        UUID id,
        String recipientName,
        String recipientContactInfo,
        NoticeType noticeType,
        NoticeStatus status,
        UUID ownerUserId,
        UUID leaseId,
        UUID tenantUserId,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        List<DeliveryAttemptResponse> deliveryAttempts) {
}
