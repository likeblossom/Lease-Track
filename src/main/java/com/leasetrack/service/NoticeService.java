package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.dto.request.CreateNoticeRequest;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.exception.NoticeNotFoundException;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.NoticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeMapper noticeMapper;
    private final Clock clock;

    public NoticeService(NoticeRepository noticeRepository, NoticeMapper noticeMapper, Clock clock) {
        this.noticeRepository = noticeRepository;
        this.noticeMapper = noticeMapper;
        this.clock = clock;
    }

    @Transactional
    public NoticeResponse createNotice(CreateNoticeRequest request) {
        Instant now = Instant.now(clock);

        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setRecipientName(request.recipientName());
        notice.setRecipientContactInfo(request.recipientContactInfo());
        notice.setNoticeType(request.noticeType());
        notice.setStatus(NoticeStatus.OPEN);
        notice.setOwnerUserId(request.ownerUserId());
        notice.setTenantUserId(request.tenantUserId());
        notice.setNotes(request.notes());
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);
        attempt.setAttemptNumber(1);
        attempt.setDeliveryMethod(request.deliveryMethod());
        attempt.setStatus(DeliveryAttemptStatus.PENDING);
        attempt.setDeadlineAt(request.deadlineAt());
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        notice.getDeliveryAttempts().add(attempt);

        return noticeMapper.toResponse(noticeRepository.save(notice));
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(UUID noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
        return noticeMapper.toResponse(notice);
    }
}
