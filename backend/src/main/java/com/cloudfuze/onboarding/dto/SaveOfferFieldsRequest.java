package com.cloudfuze.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** HR's placement of fields on the offer PDF, replacing whatever was there before. */
public record SaveOfferFieldsRequest(
        @NotEmpty(message = "Place at least one field")
        @Valid
        List<OfferFieldDto> fields
) {
}
