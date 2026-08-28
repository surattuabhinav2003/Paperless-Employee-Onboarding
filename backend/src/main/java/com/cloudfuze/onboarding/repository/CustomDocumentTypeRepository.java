package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.CustomDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomDocumentTypeRepository extends JpaRepository<CustomDocumentType, UUID> {

    Optional<CustomDocumentType> findByCode(String code);

    boolean existsByCode(String code);

    List<CustomDocumentType> findAllByOrderByPositionAscCreatedAtAsc();
}
