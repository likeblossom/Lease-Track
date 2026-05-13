package com.leasetrack.repository;

import com.leasetrack.domain.entity.Lease;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeaseRepository extends JpaRepository<Lease, UUID>, JpaSpecificationExecutor<Lease> {
}
