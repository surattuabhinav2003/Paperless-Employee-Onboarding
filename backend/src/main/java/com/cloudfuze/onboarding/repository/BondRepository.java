package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.Bond;
import com.cloudfuze.onboarding.model.BondStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface BondRepository extends JpaRepository<Bond, UUID> {

    Optional<Bond> findByCandidateId(UUID candidateId);

    @Query("select b from Bond b where b.candidate.id in :candidateIds")
    List<Bond> findByCandidateIdIn(@Param("candidateIds") Collection<UUID> candidateIds);

    Optional<Bond> findBySignatureRequestId(String signatureRequestId);

    long countByStatus(BondStatus status);
}
