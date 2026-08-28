package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** The recipient's answers, keyed by field index on the combined document. */
public record SignNocRequest(
        @NotBlank(message = "Your full name is required")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String signedByName,

        @NotNull(message = "Complete every field before submitting")
        Map<Integer, String> fieldValues
) {
}
