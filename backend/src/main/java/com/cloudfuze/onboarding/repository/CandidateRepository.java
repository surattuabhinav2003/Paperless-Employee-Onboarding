package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID>,
        JpaSpecificationExecutor<Candidate> {

    /** Portal lookups go through the token hash - the raw token is never stored. */
    Optional<Candidate> findByInviteTokenHash(String inviteTokenHash);

    Optional<Candidate> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByStage(Stage stage);

    long countByStageNot(Stage stage);

    List<Candidate> findAllByOrderByCreatedAtDesc();

    List<Candidate> findByStage(Stage stage);

    List<Candidate> findTop8ByOrderByCreatedAtDesc();

}
