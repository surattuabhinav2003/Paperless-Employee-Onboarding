package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.CustomCandidateField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomCandidateFieldRepository extends JpaRepository<CustomCandidateField, UUID> {

    Optional<CustomCandidateField> findByCode(String code);

    boolean existsByCode(String code);

    /** Live fields, in the order the form should present them. */
    List<CustomCandidateField> findByArchivedFalseOrderByPositionAscCreatedAtAsc();

    List<CustomCandidateField> findAllByOrderByPositionAscCreatedAtAsc();
}
