package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Offer acceptance is an explicit, attributable act - never an implicit page view. */
public record AcceptOfferRequest(
        @NotBlank(message = "Type your full name to accept the offer")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String acknowledgementName,

        @AssertTrue(message = "You must confirm that you accept the offer terms")
        boolean accepted
) {
}
