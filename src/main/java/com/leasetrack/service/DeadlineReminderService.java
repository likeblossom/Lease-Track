package com.leasetrack.service;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.repository.DeliveryAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadlineReminderService {

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NoticeEventPublisher noticeEventPublisher;
    private final AuditService auditService;
    private final Clock clock;
    private final long reminderWindowHours;

    public DeadlineReminderService(
            DeliveryAttemptRepository deliveryAttemptRepository,
            NoticeEventPublisher noticeEventPublisher,
            AuditService auditService,
            Clock clock,
            @Value("${app.schedulers.deadline.reminder-window-hours}") long reminderWindowHours) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.noticeEventPublisher = noticeEventPublisher;
        this.auditService = auditService;
        this.clock = clock;
        this.reminderWindowHours = reminderWindowHours;
    }

    @Transactional
    public int publishDueReminders() {
        Instant now = Instant.now(clock);
        Instant windowEnd = now.plus(reminderWindowHours, ChronoUnit.HOURS);
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByDeadlineAtBetweenAndDeadlineReminderSentFalseAndStatusIn(
                now,
                windowEnd,
                EnumSet.of(DeliveryAttemptStatus.PENDING, DeliveryAttemptStatus.SENT));

        for (DeliveryAttempt attempt : attempts) {
            attempt.setDeadlineReminderSent(true);
            attempt.setUpdatedAt(now);
            auditService.recordDeadlineApproachingPublished(attempt);
            noticeEventPublisher.publishDeadlineApproaching(attempt);
        }
        return attempts.size();
    }
}
