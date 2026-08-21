package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BloodGroup;
import com.cloudfuze.onboarding.model.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** The personal and education details the candidate submits from the portal. */
public record CandidateProfileRequest(

        @NotBlank(message = "Full name as per Aadhaar is required")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String fullNameAsPerAadhaar,

        @NotBlank(message = "Personal email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 180, message = "Email must be 180 characters or fewer")
        String personalEmail,

        @NotBlank(message = "Contact number is required")
        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{7,19}$",
                message = "Enter a valid contact number (8-20 digits, optional + prefix)")
        String contactNumber,

        @NotBlank(message = "Alternate contact number is required")
        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{7,19}$",
                message = "Enter a valid alternate contact number")
        String alternateContactNumber,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotNull(message = "Gender is required")
        Gender gender,

        @NotBlank(message = "Father's name is required")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String fathersName,

        @NotBlank(message = "Permanent address is required")
        @Size(min = 10, max = 600, message = "Give the full address (10 to 600 characters)")
        String permanentAddress,

        @NotNull(message = "Blood group is required")
        BloodGroup bloodGroup
) {
}
