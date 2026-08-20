package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequiredDocumentRequest(
        @NotNull(message = "Document type is required")
        DocumentType type,

        boolean mandatory,

        @Size(max = 160, message = "Label must be 160 characters or fewer")
        String label
) {
}
