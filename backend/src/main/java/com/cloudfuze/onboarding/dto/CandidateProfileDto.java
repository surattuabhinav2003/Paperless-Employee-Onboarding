package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BloodGroup;
import com.cloudfuze.onboarding.model.CandidateProfile;
import com.cloudfuze.onboarding.model.Gender;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
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
        Instant submittedAt,
        Instant updatedAt,
        int revision
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
                profile.getGender().getLabel(),
                profile.getFathersName(),
                profile.getPermanentAddress(),
                profile.getBloodGroup(),
                profile.getBloodGroup().getLabel(),
                profile.getSubmittedAt(),
                profile.getUpdatedAt(),
                profile.getRevision());
    }
}
