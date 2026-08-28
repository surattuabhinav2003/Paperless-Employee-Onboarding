package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.HrRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An admin adding someone to the console.
 *
 * <p>No password: console accounts sign in with Microsoft, so all this record
 * does is decide the address and the role that will be waiting when they first
 * sign in.
 */
public record CreateHrUserRequest(
        @NotBlank(message = "Work email is required")
        @Email(message = "Enter a valid work email address")
        @Size(max = 180, message = "Email must be 180 characters or fewer")
        String email,

        /** Optional - Microsoft supplies the real name on first sign-in. */
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String fullName,

        @Size(max = 120, message = "Job title must be 120 characters or fewer")
        String jobTitle,

        @NotNull(message = "Choose whether this person is an administrator")
        HrRole role
) {
}
