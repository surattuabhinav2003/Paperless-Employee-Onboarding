package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BloodGroup;
import com.cloudfuze.onboarding.model.CandidateProfile;
import com.cloudfuze.onboarding.model.EmergencyContactRelation;
import com.cloudfuze.onboarding.model.Gender;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CandidateProfileDto(
        UUID id,
        String fullNameAsPerAadhaar,
        String personalEmail,
        String contactNumber,
        String alternateContactNumber,
        LocalDate dateOfBirth,
        Gender gender,
        String genderLabel,
        String fathersName,
        String permanentAddress,
        BloodGroup bloodGroup,
        String bloodGroupLabel,
        String aadhaarNumber,
        String panNumber,
        String emergencyContactName,
        EmergencyContactRelation emergencyContactRelation,
        String emergencyContactRelationLabel,
        String emergencyContactNumber,
        Instant submittedAt,
        Instant updatedAt,
        int revision,
        /** Answers to admin-created fields, keyed by field code. */
        Map<String, String> customFields
) {
    public static CandidateProfileDto from(CandidateProfile profile) {
        if (profile == null) {
            return null;
        }
        return new CandidateProfileDto(
                profile.getId(),
                profile.getFullNameAsPerAadhaar(),
                profile.getPersonalEmail(),
                profile.getContactNumber(),
                profile.getAlternateContactNumber(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getGender() == null ? null : profile.getGender().getLabel(),
                profile.getFathersName(),
                profile.getPermanentAddress(),
                profile.getBloodGroup(),
                profile.getBloodGroup() == null ? null : profile.getBloodGroup().getLabel(),
                profile.getAadhaarNumber(),
                profile.getPanNumber(),
                profile.getEmergencyContactName(),
                profile.getEmergencyContactRelation(),
                profile.getEmergencyContactRelation() == null ? null : profile.getEmergencyContactRelation().getLabel(),
                profile.getEmergencyContactNumber(),
                profile.getSubmittedAt(),
                profile.getUpdatedAt(),
                profile.getRevision(),
                profile.getCustomValues() == null ? Map.of() : Map.copyOf(profile.getCustomValues()));
    }
}
