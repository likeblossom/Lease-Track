package com.leasetrack.repository;

import com.leasetrack.domain.entity.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByNoticeIdOrderByCreatedAtAsc(UUID noticeId);
}
