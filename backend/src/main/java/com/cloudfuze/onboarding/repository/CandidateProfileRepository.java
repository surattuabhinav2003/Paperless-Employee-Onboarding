package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    Optional<CandidateProfile> findByCandidateId(UUID candidateId);

    boolean existsByCandidateId(UUID candidateId);

    @Query("select p from CandidateProfile p where p.candidate.id in :candidateIds")
    List<CandidateProfile> findByCandidateIdIn(@Param("candidateIds") Collection<UUID> candidateIds);
}
