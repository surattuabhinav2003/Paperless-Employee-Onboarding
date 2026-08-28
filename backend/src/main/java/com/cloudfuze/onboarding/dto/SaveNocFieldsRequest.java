package com.cloudfuze.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Where HR placed the fields on the combined document. */
public record SaveNocFieldsRequest(
        @NotNull(message = "Fields are required")
        @Valid
        List<OfferFieldDto> fields
) {
}
