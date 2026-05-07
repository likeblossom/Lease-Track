package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.AuditEvent;
import com.leasetrack.domain.entity.DeliveryAttempt;
import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.ActorRole;
import com.leasetrack.domain.enums.AuditEventType;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);
        auditService = new AuditService(auditEventRepository, clock);
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordNoticeCreatedPersistsSystemAuditEvent() {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());
        notice.setNoticeType(NoticeType.RENT_INCREASE);
        notice.setStatus(NoticeStatus.OPEN);

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        notice.getDeliveryAttempts().add(attempt);

        auditService.recordNoticeCreated(notice);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent auditEvent = captor.getValue();
        assertThat(auditEvent.getNoticeId()).isEqualTo(notice.getId());
        assertThat(auditEvent.getDeliveryAttemptId()).isEqualTo(attempt.getId());
        assertThat(auditEvent.getEventType()).isEqualTo(AuditEventType.NOTICE_CREATED);
        assertThat(auditEvent.getActorRole()).isEqualTo(ActorRole.SYSTEM);
        assertThat(auditEvent.getCreatedAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(auditEvent.getDetails()).contains("RENT_INCREASE", "OPEN");
    }

    @Test
    void recordDeliveryStatusUpdatedPersistsOldAndNewStatus() {
        Notice notice = new Notice();
        notice.setId(UUID.randomUUID());

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setNotice(notice);

        auditService.recordDeliveryStatusUpdated(
                attempt,
                DeliveryAttemptStatus.PENDING,
                DeliveryAttemptStatus.SENT);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent auditEvent = captor.getValue();
        assertThat(auditEvent.getEventType()).isEqualTo(AuditEventType.DELIVERY_STATUS_UPDATED);
        assertThat(auditEvent.getDetails()).contains("PENDING", "SENT");
    }
}
