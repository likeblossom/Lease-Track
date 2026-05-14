package com.leasetrack.mapper;

import com.leasetrack.domain.entity.Lease;
import com.leasetrack.domain.entity.LeaseEvent;
import com.leasetrack.domain.entity.PropertyUnit;
import com.leasetrack.domain.enums.LeaseStatus;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.dto.response.LeaseEventResponse;
import com.leasetrack.dto.response.LeaseResponse;
import com.leasetrack.dto.response.LeaseSummaryResponse;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class LeaseMapper {

    private final NoticeMapper noticeMapper;
    private final Clock clock;

    public LeaseMapper(NoticeMapper noticeMapper, Clock clock) {
        this.noticeMapper = noticeMapper;
        this.clock = clock;
    }

    public LeaseSummaryResponse toSummaryResponse(Lease lease) {
        long openNoticeCount = lease.getNotices().stream()
                .filter(notice -> notice.getStatus() == NoticeStatus.OPEN)
                .count();
        PropertyUnit unit = lease.getUnit();
        return new LeaseSummaryResponse(
                lease.getId(),
                lease.getName(),
                lease.getPropertyAddress(),
                unit == null ? null : unit.getId(),
                unit == null ? null : unit.getUnitLabel(),
                lease.getTenantNames(),
                lease.getLeaseStartDate(),
                lease.getLeaseEndDate(),
                lease.getRentCents(),
                displayStatus(lease),
                lease.getRenewalDecisionDueDate(),
                lease.getNotices().size(),
                openNoticeCount,
                lease.getCreatedAt(),
                lease.getUpdatedAt());
    }

    public LeaseResponse toResponse(Lease lease) {
        PropertyUnit unit = lease.getUnit();
        return new LeaseResponse(
                lease.getId(),
                lease.getName(),
                lease.getPropertyAddress(),
                unit == null ? null : unit.getId(),
                unit == null ? null : unit.getUnitLabel(),
                lease.getTenantNames(),
                lease.getTenantEmail(),
                lease.getTenantPhone(),
                lease.getLeaseStartDate(),
                lease.getLeaseEndDate(),
                lease.getRentCents(),
                lease.getSecurityDepositCents(),
                displayStatus(lease),
                lease.getRenewalDecisionDueDate(),
                lease.getTerminatedAt(),
                lease.getOwnerUserId(),
                lease.getNotes(),
                lease.getCreatedAt(),
                lease.getUpdatedAt(),
                lease.getEvents().stream()
                        .map(this::toEventResponse)
                        .toList(),
                lease.getNotices().stream()
                        .map(noticeMapper::toSummaryResponse)
                        .toList());
    }

    public LeaseEventResponse toEventResponse(LeaseEvent event) {
        return new LeaseEventResponse(
                event.getId(),
                event.getLease().getId(),
                event.getEventType(),
                event.getActorRole(),
                event.getActorReference(),
                event.getDetails(),
                event.getCreatedAt());
    }

    private LeaseStatus displayStatus(Lease lease) {
        if (lease.getStatus() == LeaseStatus.ACTIVE
                && !lease.getLeaseEndDate().isBefore(LocalDate.now(clock))
                && !lease.getLeaseEndDate().isAfter(LocalDate.now(clock).plusDays(60))) {
            return LeaseStatus.EXPIRING_SOON;
        }
        if (lease.getStatus() == LeaseStatus.ACTIVE && lease.getLeaseEndDate().isBefore(LocalDate.now(clock))) {
            return LeaseStatus.EXPIRED;
        }
        return lease.getStatus();
    }
}
