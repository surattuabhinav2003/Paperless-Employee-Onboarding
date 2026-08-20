package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByCandidateId(UUID candidateId);

    @Query("select o from Offer o where o.candidate.id in :candidateIds")
    List<Offer> findByCandidateIdIn(@Param("candidateIds") Collection<UUID> candidateIds);

    List<Offer> findByStatus(OfferStatus status);

    long countByStatus(OfferStatus status);
}
