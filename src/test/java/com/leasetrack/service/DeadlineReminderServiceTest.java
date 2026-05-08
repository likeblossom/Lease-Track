package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.event.publisher.NoticeEventPublisher;
import com.leasetrack.repository.DeliveryAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadlineReminderServiceTest {

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private NoticeEventPublisher noticeEventPublisher;

    @Mock
    private AuditService auditService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
    private DeadlineReminderService deadlineReminderService;

    @BeforeEach
    void setUp() {
        deadlineReminderService = new DeadlineReminderService(
                deliveryAttemptRepository,
                noticeEventPublisher,
                auditService,
                clock,
                72);
    }

    @Test
    void publishDueRemindersMarksAttemptAndPublishesEvent() {
        DeliveryAttempt attempt = attemptWithDeadline(Instant.parse("2026-05-08T12:00:00Z"));

        when(deliveryAttemptRepository.findByDeadlineAtBetweenAndDeadlineReminderSentFalseAndStatusIn(
                eq(Instant.parse("2026-05-06T12:00:00Z")),
                eq(Instant.parse("2026-05-09T12:00:00Z")),
                org.mockito.ArgumentMatchers.<Collection<DeliveryAttemptStatus>>any()))
                .thenReturn(List.of(attempt));

        int reminderCount = deadlineReminderService.publishDueReminders();

        assertThat(reminderCount).isEqualTo(1);
        assertThat(attempt.isDeadlineReminderSent()).isTrue();
        assertThat(attempt.getUpdatedAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        verify(auditService).recordDeadlineApproachingPublished(attempt);
        verify(noticeEventPublisher).publishDeadlineApproaching(attempt);
    }

    private DeliveryAttempt attemptWithDeadline(Instant deadlineAt) {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);
        attempt.setDeliveryMethod(DeliveryMethod.REGISTERED_MAIL);
        attempt.setStatus(DeliveryAttemptStatus.SENT);
        attempt.setDeadlineAt(deadlineAt);
        attempt.setCreatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        attempt.setUpdatedAt(Instant.parse("2026-05-01T12:00:00Z"));
        return attempt;
    }
}
