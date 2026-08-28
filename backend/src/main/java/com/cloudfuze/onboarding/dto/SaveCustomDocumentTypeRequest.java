package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An admin creating or editing a document type of their own. */
public record SaveCustomDocumentTypeRequest(
        @NotBlank(message = "Give the document a name")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String label,

        @Size(max = 300, message = "Description must be 300 characters or fewer")
        String description,

        DocumentType.Group group,

        Boolean enabled
) {
}
