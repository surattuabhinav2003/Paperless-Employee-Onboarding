package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateProfile;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateProfileRepository;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The candidate's own personal and education details. Editable while documents
 * are still open; frozen once HR has approved the document stage, so the details
 * HR reviewed cannot change underneath them.
 */
@Service
public class CandidateProfileService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileService.class);

    private final CandidateProfileRepository profileRepository;
    private final DocumentApprovalService approvalService;
    private final AuditService auditService;

    public CandidateProfileService(CandidateProfileRepository profileRepository,
                                  DocumentApprovalService approvalService, AuditService auditService) {
        this.profileRepository = profileRepository;
        this.approvalService = approvalService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<CandidateProfile> find(UUID candidateId) {
        return profileRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public CandidateProfileDto view(UUID candidateId) {
        return profileRepository.findByCandidateId(candidateId).map(CandidateProfileDto::from).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isComplete(UUID candidateId) {
        return profileRepository.existsByCandidateId(candidateId);
    }

    /** Creates or updates the candidate's details. */
    @Transactional
    public CandidateProfileDto save(Candidate candidate, CandidateProfileRequest request, String ipAddress) {
        CandidateProfile profile = profileRepository.findByCandidateId(candidate.getId()).orElse(null);
        boolean isNew = profile == null;

        if (!isNew && candidate.getStage() != Stage.DOCS_PENDING) {
            throw new BusinessRuleException("PROFILE_LOCKED",
                    "Your details were already reviewed by HR and can no longer be changed here. "
                            + "Contact HR if something needs correcting.");
        }
        if (!isNew && candidate.isSubmittedForReview()) {
            throw new BusinessRuleException("PROFILE_LOCKED",
                    "You have already submitted your onboarding pack to HR, so these details are locked. "
                            + "Contact HR if something needs correcting.");
        }

        if (isNew) {
            profile = new CandidateProfile(candidate);
        } else {
            profile.setRevision(profile.getRevision() + 1);
        }

        profile.setFullNameAsPerAadhaar(request.fullNameAsPerAadhaar().trim());
        profile.setPersonalEmail(request.personalEmail().trim().toLowerCase());
        profile.setContactNumber(request.contactNumber().trim());
        profile.setAlternateContactNumber(blankToNull(request.alternateContactNumber()));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setGender(request.gender());
        profile.setFathersName(request.fathersName().trim());
        profile.setPermanentAddress(request.permanentAddress().trim());
        profile.setBloodGroup(request.bloodGroup());
        profile.setSubmittedFromIp(ipAddress);
        if (isNew) {
            profile.setSubmittedAt(Instant.now());
        }
        profileRepository.save(profile);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gender", profile.getGender().getCode());
        metadata.put("bloodGroup", profile.getBloodGroup().getCode());
        metadata.put("revision", profile.getRevision());
        auditService.recordCandidateEvent(candidate.getId(),
                isNew ? AuditEventType.PROFILE_SUBMITTED : AuditEventType.PROFILE_UPDATED,
                candidate.getEmail(), ipAddress, null, metadata);

        log.info("Candidate {} {} their details (revision {})", candidate.getEmail(),
                isNew ? "submitted" : "updated", profile.getRevision());

        // The details may be the last missing piece, so re-check the document gate.
        approvalService.evaluate(candidate);
        return CandidateProfileDto.from(profile);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
