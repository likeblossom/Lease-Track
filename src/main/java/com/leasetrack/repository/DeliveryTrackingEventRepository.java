package com.leasetrack.repository;

import com.leasetrack.domain.entity.DeliveryTrackingEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryTrackingEventRepository extends JpaRepository<DeliveryTrackingEvent, UUID> {

    List<DeliveryTrackingEvent> findByDeliveryAttempt_Notice_IdOrderByCheckedAtAsc(UUID noticeId);
}
