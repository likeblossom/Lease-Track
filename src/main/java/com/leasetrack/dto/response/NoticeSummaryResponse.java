package com.leasetrack.dto.response;

import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import java.time.Instant;
import java.util.UUID;

public record NoticeSummaryResponse(
        UUID id,
        String recipientName,
        NoticeType noticeType,
        NoticeStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
