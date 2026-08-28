package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.CandidateField;
import com.cloudfuze.onboarding.model.CandidateFieldSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateFieldSettingRepository
        extends JpaRepository<CandidateFieldSetting, CandidateField> {
}
