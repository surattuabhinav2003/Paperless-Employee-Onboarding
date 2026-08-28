package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.CandidateFieldDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.exception.FieldValidationException;
import com.cloudfuze.onboarding.model.CandidateField;
import com.cloudfuze.onboarding.model.CandidateFieldSetting;
import com.cloudfuze.onboarding.repository.CandidateFieldSettingRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which personal details candidates are asked for, and which are required.
 *
 * <p>Requiredness used to be Bean Validation annotations on the request, which
 * cannot vary at runtime. It lives here instead so an administrator can change
 * it without a deploy - the annotations that remain on the request are purely
 * about <em>format</em> (a valid email, a valid PAN), and those are unaffected
 * by whether the field is asked for at all.
 */
@Service
public class CandidateFieldService {

    private static final Logger log = LoggerFactory.getLogger(CandidateFieldService.class);

    private final CandidateFieldSettingRepository repository;

    public CandidateFieldService(CandidateFieldSettingRepository repository) {
        this.repository = repository;
    }

    /** The effective setting for every field, defaults filled in. */
    @Transactional(readOnly = true)
    public Map<CandidateField, Setting> settings() {
        Map<CandidateField, CandidateFieldSetting> stored = new EnumMap<>(CandidateField.class);
        repository.findAll().forEach(s -> stored.put(s.getField(), s));

        Map<CandidateField, Setting> effective = new EnumMap<>(CandidateField.class);
        for (CandidateField field : CandidateField.values()) {
            CandidateFieldSetting override = stored.get(field);
            effective.put(field, override == null
                    ? new Setting(true, field.isRequiredByDefault())
                    : new Setting(override.isEnabled(), override.isRequired()));
        }
        return effective;
    }

    @Transactional(readOnly = true)
    public List<CandidateFieldDto> list() {
        Map<CandidateField, Setting> effective = settings();
        return Arrays.stream(CandidateField.values())
                .map(field -> {
                    Setting setting = effective.get(field);
                    return new CandidateFieldDto(field.getCode(), field.getLabel(),
                            field.group().name().toLowerCase(), field.group().getLabel(),
                            setting.enabled(), setting.required());
                })
                .toList();
    }

    /**
     * Saves one field's setting.
     *
     * <p>A disabled field is also stored as not required: "off but required"
     * would be unsatisfiable, and silently blocking every submission is a worse
     * outcome than quietly correcting an impossible combination.
     */
    @Transactional
    public List<CandidateFieldDto> update(CandidateField field, boolean enabled, boolean required,
                                          HrPrincipal actor) {
        boolean effectiveRequired = enabled && required;
        CandidateFieldSetting setting = repository.findById(field)
                .orElseGet(() -> new CandidateFieldSetting(field, true, field.isRequiredByDefault()));
        setting.setEnabled(enabled);
        setting.setRequired(effectiveRequired);
        repository.save(setting);

        log.info("Admin {} set candidate field {} to enabled={} required={}",
                actor.getEmail(), field.getCode(), enabled, effectiveRequired);
        return list();
    }

    /**
     * Rejects a submission that leaves a required field empty.
     *
     * <p>Runs after Bean Validation, so anything present is already known to be
     * well formed; this only decides whether being absent is acceptable.
     */
    public void validateRequired(CandidateProfileRequest request) {
        Map<String, String> errors = missingRequired(request);
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Request validation failed.", errors);
        }
    }

    /** Required fields left empty, keyed by the JSON property name. */
    public Map<String, String> missingRequired(CandidateProfileRequest request) {
        Map<CandidateField, Setting> effective = settings();
        Map<String, String> errors = new LinkedHashMap<>();

        for (CandidateField field : CandidateField.values()) {
            Setting setting = effective.get(field);
            if (!setting.enabled() || !setting.required()) {
                continue;
            }
            if (isBlank(valueOf(request, field))) {
                errors.put(propertyOf(field), field.getLabel() + " is required");
            }
        }

        return errors;
    }

    /** Whether a field is asked for at all - used to ignore values for disabled fields. */
    public boolean isEnabled(CandidateField field) {
        return settings().get(field).enabled();
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static Object valueOf(CandidateProfileRequest r, CandidateField field) {
        return switch (field) {
            case FULL_NAME_AS_PER_AADHAAR -> r.fullNameAsPerAadhaar();
            case PERSONAL_EMAIL -> r.personalEmail();
            case CONTACT_NUMBER -> r.contactNumber();
            case ALTERNATE_CONTACT_NUMBER -> r.alternateContactNumber();
            case DATE_OF_BIRTH -> r.dateOfBirth();
            case GENDER -> r.gender();
            case FATHERS_NAME -> r.fathersName();
            case PERMANENT_ADDRESS -> r.permanentAddress();
            case BLOOD_GROUP -> r.bloodGroup();
            case AADHAAR_NUMBER -> r.aadhaarNumber();
            case PAN_NUMBER -> r.panNumber();
            case EMERGENCY_CONTACT_NAME -> r.emergencyContactName();
            case EMERGENCY_CONTACT_RELATION -> r.emergencyContactRelation();
            case EMERGENCY_CONTACT_NUMBER -> r.emergencyContactNumber();
        };
    }

    /** The JSON property name, so the error lands on the right form input. */
    private static String propertyOf(CandidateField field) {
        return switch (field) {
            case FULL_NAME_AS_PER_AADHAAR -> "fullNameAsPerAadhaar";
            case PERSONAL_EMAIL -> "personalEmail";
            case CONTACT_NUMBER -> "contactNumber";
            case ALTERNATE_CONTACT_NUMBER -> "alternateContactNumber";
            case DATE_OF_BIRTH -> "dateOfBirth";
            case GENDER -> "gender";
            case FATHERS_NAME -> "fathersName";
            case PERMANENT_ADDRESS -> "permanentAddress";
            case BLOOD_GROUP -> "bloodGroup";
            case AADHAAR_NUMBER -> "aadhaarNumber";
            case PAN_NUMBER -> "panNumber";
            case EMERGENCY_CONTACT_NAME -> "emergencyContactName";
            case EMERGENCY_CONTACT_RELATION -> "emergencyContactRelation";
            case EMERGENCY_CONTACT_NUMBER -> "emergencyContactNumber";
        };
    }

    /** Effective setting for one field. */
    public record Setting(boolean enabled, boolean required) {
    }
}
