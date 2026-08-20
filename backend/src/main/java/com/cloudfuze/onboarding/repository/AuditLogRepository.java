package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByCandidateIdOrderByOccurredAtDesc(UUID candidateId);

    List<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
