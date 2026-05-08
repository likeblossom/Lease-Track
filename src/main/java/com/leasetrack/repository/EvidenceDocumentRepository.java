package com.leasetrack.repository;

import com.leasetrack.domain.entity.EvidenceDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceDocumentRepository extends JpaRepository<EvidenceDocument, UUID> {

    List<EvidenceDocument> findByNoticeIdOrderByCreatedAtAsc(UUID noticeId);

    List<EvidenceDocument> findByDeliveryAttempt_IdOrderByCreatedAtAsc(UUID deliveryAttemptId);
}
