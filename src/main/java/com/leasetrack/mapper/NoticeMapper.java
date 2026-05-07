package com.leasetrack.mapper;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.dto.response.DeliveryAttemptResponse;
import com.leasetrack.dto.response.NoticeResponse;
import org.springframework.stereotype.Component;

@Component
public class NoticeMapper {

    public NoticeResponse toResponse(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getRecipientName(),
                notice.getRecipientContactInfo(),
                notice.getNoticeType(),
                notice.getStatus(),
                notice.getOwnerUserId(),
                notice.getTenantUserId(),
                notice.getNotes(),
                notice.getCreatedAt(),
                notice.getUpdatedAt(),
                notice.getClosedAt(),
                notice.getDeliveryAttempts().stream()
                        .map(this::toResponse)
                        .toList());
    }

    private DeliveryAttemptResponse toResponse(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getDeliveryMethod(),
                attempt.getStatus(),
                attempt.getSentAt(),
                attempt.getDeliveredAt(),
                attempt.getDeadlineAt(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt());
    }
}
