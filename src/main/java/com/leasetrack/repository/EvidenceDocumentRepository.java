package com.leasetrack.repository;

import com.leasetrack.domain.entity.EvidenceDocument;
import com.leasetrack.domain.enums.EvidenceDocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceDocumentRepository extends JpaRepository<EvidenceDocument, UUID> {

    List<EvidenceDocument> findByNoticeIdOrderByCreatedAtAsc(UUID noticeId);

    List<EvidenceDocument> findByDeliveryAttempt_IdOrderByCreatedAtAsc(UUID deliveryAttemptId);

    boolean existsByDeliveryAttempt_IdAndDocumentType(UUID deliveryAttemptId, EvidenceDocumentType documentType);
}
