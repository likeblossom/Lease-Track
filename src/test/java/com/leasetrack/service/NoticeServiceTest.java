package com.leasetrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.leasetrack.domain.entity.Notice;
import com.leasetrack.domain.enums.DeliveryAttemptStatus;
import com.leasetrack.domain.enums.DeliveryMethod;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.domain.enums.NoticeType;
import com.leasetrack.dto.request.CreateNoticeRequest;
import com.leasetrack.dto.response.NoticeResponse;
import com.leasetrack.mapper.NoticeMapper;
import com.leasetrack.repository.NoticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    private final NoticeMapper noticeMapper = new NoticeMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createNoticeCreatesOpenNoticeWithInitialPendingDeliveryAttempt() {
        NoticeService noticeService = new NoticeService(noticeRepository, noticeMapper, clock);
        UUID ownerUserId = UUID.randomUUID();
        UUID tenantUserId = UUID.randomUUID();
        Instant deadlineAt = Instant.parse("2026-06-01T12:00:00Z");

        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoticeResponse response = noticeService.createNotice(new CreateNoticeRequest(
                "Marie Tremblay",
                "marie@example.com",
                NoticeType.RENT_INCREASE,
                DeliveryMethod.REGISTERED_MAIL,
                ownerUserId,
                tenantUserId,
                deadlineAt,
                "Initial notice"));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(NoticeStatus.OPEN);
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.tenantUserId()).isEqualTo(tenantUserId);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-06T12:00:00Z"));
        assertThat(response.deliveryAttempts()).hasSize(1);
        assertThat(response.deliveryAttempts().getFirst().deliveryMethod()).isEqualTo(DeliveryMethod.REGISTERED_MAIL);
        assertThat(response.deliveryAttempts().getFirst().status()).isEqualTo(DeliveryAttemptStatus.PENDING);
        assertThat(response.deliveryAttempts().getFirst().deadlineAt()).isEqualTo(deadlineAt);
    }
}
