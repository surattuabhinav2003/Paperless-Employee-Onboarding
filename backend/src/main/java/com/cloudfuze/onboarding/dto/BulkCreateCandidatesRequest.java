package com.cloudfuze.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Several candidates invited in one go, all asked for the same documents.
 *
 * <p>The document list is shared deliberately: the case this exists for is a
 * batch joining the same role or intake, where re-picking the same checklist
 * per person is the tedious part. Anyone needing a different checklist is added
 * on their own.
 */
public record BulkCreateCandidatesRequest(

        @NotEmpty(message = "Add at least one candidate")
        @Size(max = 50, message = "Add up to 50 candidates at a time")
        @Valid
        List<Entry> candidates,

        @NotEmpty(message = "Select at least one required document")
        @Valid
        List<RequiredDocumentRequest> requiredDocuments
) {

    /** One person in the batch. */
    public record Entry(
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
            String department
    ) {
    }
}
