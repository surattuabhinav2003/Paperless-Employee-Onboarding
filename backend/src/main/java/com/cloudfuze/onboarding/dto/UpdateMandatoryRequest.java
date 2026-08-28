package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMandatoryRequest(
        @NotNull(message = "Mandatory flag is required")
        Boolean mandatory
) {
}
