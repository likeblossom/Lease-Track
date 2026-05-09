package com.leasetrack.repository;

import com.leasetrack.domain.entity.EvidencePackageSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidencePackageSnapshotRepository extends JpaRepository<EvidencePackageSnapshot, UUID> {
}
