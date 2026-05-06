package com.leasetrack.repository;

import com.leasetrack.domain.entity.Notice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    List<Notice> findAllByOrderByCreatedAtDesc();
}
