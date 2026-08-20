package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rejecting a document always requires a reason the candidate can act on. */
public record RejectDocumentRequest(
        @NotBlank(message = "A rejection reason is required")
        @Size(min = 5, max = 600, message = "Give a reason between 5 and 600 characters")
        String reason
) {
}
