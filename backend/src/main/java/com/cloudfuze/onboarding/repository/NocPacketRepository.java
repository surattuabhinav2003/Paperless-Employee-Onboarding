package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.NocPacket;
import com.cloudfuze.onboarding.model.NocStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NocPacketRepository extends JpaRepository<NocPacket, UUID> {

    Optional<NocPacket> findByAccessTokenHash(String accessTokenHash);

    Page<NocPacket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<NocPacket> findByStatusOrderByCreatedAtDesc(NocStatus status, Pageable pageable);

    long countByStatus(NocStatus status);
}
