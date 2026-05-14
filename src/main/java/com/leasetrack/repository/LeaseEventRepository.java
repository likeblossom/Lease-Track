package com.leasetrack.repository;

import com.leasetrack.domain.entity.LeaseEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseEventRepository extends JpaRepository<LeaseEvent, UUID> {

    List<LeaseEvent> findByLeaseIdOrderByCreatedAtAsc(UUID leaseId);
}
