package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.HrUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HrUserRepository extends JpaRepository<HrUser, UUID> {

    Optional<HrUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
