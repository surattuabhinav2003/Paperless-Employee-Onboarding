package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignBondRequest(
        @NotBlank(message = "Type your full legal name to sign")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String signerFullName,

        @AssertTrue(message = "You must agree to the bond terms before signing")
        boolean consent
) {
}
