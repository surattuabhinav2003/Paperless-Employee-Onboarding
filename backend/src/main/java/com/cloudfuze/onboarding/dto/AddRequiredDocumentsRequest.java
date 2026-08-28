package com.cloudfuze.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddRequiredDocumentsRequest(
        @NotEmpty(message = "Select at least one document to add")
        @Valid
        List<RequiredDocumentRequest> requiredDocuments
) {
}
