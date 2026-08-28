package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequiredDocumentRequest(
        /* A code rather than the enum: it may name a type an admin created.
           Checked against the catalogue, which knows about both kinds. */
        @NotBlank(message = "Document type is required")
        String type,

        boolean mandatory,

        @Size(max = 160, message = "Label must be 160 characters or fewer")
        String label
) {
}
