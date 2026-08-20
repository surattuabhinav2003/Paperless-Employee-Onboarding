package com.cloudfuze.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCandidateRequest(
        @NotBlank(message = "Candidate name is required")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 180, message = "Email must be 180 characters or fewer")
        String email,

        @NotBlank(message = "Role is required")
        @Size(max = 120, message = "Role must be 120 characters or fewer")
        String role,

        @NotBlank(message = "Department is required")
        @Size(max = 120, message = "Department must be 120 characters or fewer")
        String department,

        @NotEmpty(message = "Select at least one required document")
        @Valid
        List<RequiredDocumentRequest> requiredDocuments
) {
}
