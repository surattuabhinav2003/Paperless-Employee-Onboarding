package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateProfile;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateProfileRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.FieldValidationException;
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
    private final AuditService auditService;
    private final CandidateFieldService fieldService;
    private final CustomCandidateFieldService customFieldService;

    public CandidateProfileService(CandidateProfileRepository profileRepository, AuditService auditService,
                                  CandidateFieldService fieldService,
                                  CustomCandidateFieldService customFieldService) {
        this.profileRepository = profileRepository;
        this.auditService = auditService;
        this.fieldService = fieldService;
        this.customFieldService = customFieldService;
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

    /**
     * HR correcting the candidate's details on their behalf.
     *
     * <p>None of the candidate-facing locks apply: the whole reason those locks
     * tell the candidate to "contact HR" is that HR is the one who can still fix
     * a typo afterwards. Recorded against the HR user, not the candidate, so the
     * audit trail never implies the candidate changed their own details.
     */
    @Transactional
    public CandidateProfileDto saveAsHr(Candidate candidate, CandidateProfileRequest request,
                                        HrPrincipal hrUser, String ipAddress) {
        CandidateProfile profile = profileRepository.findByCandidateId(candidate.getId()).orElse(null);
        boolean isNew = profile == null;
        if (isNew) {
            profile = new CandidateProfile(candidate);
            profile.setSubmittedAt(Instant.now());
        } else {
            profile.setRevision(profile.getRevision() + 1);
        }

        validateAll(request);
        apply(profile, request, ipAddress);
        profileRepository.save(profile);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.PROFILE_CORRECTED, hrUser.getEmail(),
                ipAddress, null, Map.of("revision", profile.getRevision(), "createdByHr", isNew));

        log.info("HR {} {} the details for {} (revision {})", hrUser.getEmail(),
                isNew ? "entered" : "corrected", candidate.getEmail(), profile.getRevision());

        return CandidateProfileDto.from(profile);
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

        validateAll(request);
        apply(profile, request, ipAddress);
        if (isNew) {
            profile.setSubmittedAt(Instant.now());
        }
        profileRepository.save(profile);

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (profile.getGender() != null) {
            metadata.put("gender", profile.getGender().getCode());
        }
        if (profile.getBloodGroup() != null) {
            metadata.put("bloodGroup", profile.getBloodGroup().getCode());
        }
        metadata.put("revision", profile.getRevision());
        auditService.recordCandidateEvent(candidate.getId(),
                isNew ? AuditEventType.PROFILE_SUBMITTED : AuditEventType.PROFILE_UPDATED,
                candidate.getEmail(), ipAddress, null, metadata);

        log.info("Candidate {} {} their details (revision {})", candidate.getEmail(),
                isNew ? "submitted" : "updated", profile.getRevision());

        return CandidateProfileDto.from(profile);
    }

    /**
     * Copies a validated request onto the profile. Shared by the candidate's own
     * submission and HR's correction so the two can never normalise a value
     * differently - an Aadhaar number saved by HR is stored exactly as one saved
     * by the candidate.
     */
    private void apply(CandidateProfile profile, CandidateProfileRequest request, String ipAddress) {
        profile.setFullNameAsPerAadhaar(trimmed(request.fullNameAsPerAadhaar()));
        profile.setPersonalEmail(lowered(request.personalEmail()));
        profile.setContactNumber(trimmed(request.contactNumber()));
        profile.setAlternateContactNumber(trimmed(request.alternateContactNumber()));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setGender(request.gender());
        profile.setFathersName(trimmed(request.fathersName()));
        profile.setPermanentAddress(trimmed(request.permanentAddress()));
        profile.setBloodGroup(request.bloodGroup());
        profile.setAadhaarNumber(request.aadhaarNumber() == null ? null
                : request.aadhaarNumber().replaceAll("\s", ""));
        profile.setPanNumber(request.panNumber() == null ? null
                : request.panNumber().trim().toUpperCase());
        profile.setEmergencyContactName(trimmed(request.emergencyContactName()));
        profile.setEmergencyContactRelation(request.emergencyContactRelation());
        profile.setEmergencyContactNumber(trimmed(request.emergencyContactNumber()));
        profile.setSubmittedFromIp(ipAddress);

        // Answers to fields that are no longer asked are left as they are
        // rather than cleared, so switching a field back on does not look like
        // the candidate never answered it.
        profile.getCustomValues().putAll(customFieldService.sanitise(request.customFields()));
    }

    /**
     * Both kinds of field, judged together.
     *
     * <p>Bean Validation has already run by the time a request reaches the
     * service; this repeats the runtime part so a caller that assembles a
     * request itself cannot bypass it.
     */
    private void validateAll(CandidateProfileRequest request) {
        Map<String, String> errors = new LinkedHashMap<>(fieldService.missingRequired(request));
        errors.putAll(customFieldService.validate(request.customFields()));
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Request validation failed.", errors);
        }
    }

    /* An optional field left blank arrives as null, so trimming has to survive
       it - these ran unguarded when every field was mandatory. */
    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String lowered(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}
