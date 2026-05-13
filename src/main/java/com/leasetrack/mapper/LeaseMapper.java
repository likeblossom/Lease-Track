package com.leasetrack.mapper;

import com.leasetrack.domain.entity.Lease;
import com.leasetrack.domain.enums.NoticeStatus;
import com.leasetrack.dto.response.LeaseResponse;
import com.leasetrack.dto.response.LeaseSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class LeaseMapper {

    private final NoticeMapper noticeMapper;

    public LeaseMapper(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    public LeaseSummaryResponse toSummaryResponse(Lease lease) {
        long openNoticeCount = lease.getNotices().stream()
                .filter(notice -> notice.getStatus() == NoticeStatus.OPEN)
                .count();
        return new LeaseSummaryResponse(
                lease.getId(),
                lease.getName(),
                lease.getPropertyAddress(),
                lease.getTenantNames(),
                lease.getLeaseStartDate(),
                lease.getLeaseEndDate(),
                lease.getNotices().size(),
                openNoticeCount,
                lease.getCreatedAt(),
                lease.getUpdatedAt());
    }

    public LeaseResponse toResponse(Lease lease) {
        return new LeaseResponse(
                lease.getId(),
                lease.getName(),
                lease.getPropertyAddress(),
                lease.getTenantNames(),
                lease.getTenantEmail(),
                lease.getTenantPhone(),
                lease.getLeaseStartDate(),
                lease.getLeaseEndDate(),
                lease.getOwnerUserId(),
                lease.getNotes(),
                lease.getCreatedAt(),
                lease.getUpdatedAt(),
                lease.getNotices().stream()
                        .map(noticeMapper::toSummaryResponse)
                        .toList());
    }
}
