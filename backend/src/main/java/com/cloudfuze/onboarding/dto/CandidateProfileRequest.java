package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BloodGroup;
import com.cloudfuze.onboarding.model.EmergencyContactRelation;
import com.cloudfuze.onboarding.model.Gender;
import com.cloudfuze.onboarding.validation.DistinctEmergencyContact;
import com.cloudfuze.onboarding.validation.RequiredCandidateFields;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

/**
 * The personal and education details the candidate submits from the portal.
 *
 * <p>The annotations here are about <em>format</em> only - a valid email, a
 * valid PAN. Whether a field must be filled in at all is an administrator
 * setting, enforced by {@link com.cloudfuze.onboarding.service.CandidateFieldService},
 * because an annotation cannot change at runtime. Every constraint below passes
 * on a null, so an absent optional field is accepted here and judged there.
 */
@DistinctEmergencyContact
@RequiredCandidateFields
public record CandidateProfileRequest(
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String fullNameAsPerAadhaar,
        @Email(message = "Enter a valid email address")
        @Size(max = 180, message = "Email must be 180 characters or fewer")
        String personalEmail,
        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{7,19}$",
                message = "Enter a valid contact number (8-20 digits, optional + prefix)")
        String contactNumber,
        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{7,19}$",
                message = "Enter a valid alternate contact number")
        String alternateContactNumber,
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String fathersName,
        @Size(min = 10, max = 600, message = "Give the full address (10 to 600 characters)")
        String permanentAddress,
        BloodGroup bloodGroup,
        @Pattern(regexp = "^\\d{4}\\s?\\d{4}\\s?\\d{4}$", message = "Enter a valid 12-digit Aadhaar number")
        String aadhaarNumber,
        @Pattern(regexp = "^[A-Za-z]{5}[0-9]{4}[A-Za-z]$", message = "Enter a valid PAN (e.g. ABCDE1234F)")
        String panNumber,
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String emergencyContactName,
        EmergencyContactRelation emergencyContactRelation,
        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{7,19}$",
                message = "Enter a valid emergency contact number")
        String emergencyContactNumber,

        /**
         * Answers to the fields an admin created, keyed by field code. Checked
         * by {@link com.cloudfuze.onboarding.service.CustomCandidateFieldService}
         * rather than here, for the same reason as requiredness: the fields do
         * not exist until someone makes them.
         */
        Map<String, String> customFields
) {
    /* Never null downstream, so no caller has to guard the map. */
    public Map<String, String> customFields() {
        return customFields == null ? Map.of() : customFields;
    }
}
