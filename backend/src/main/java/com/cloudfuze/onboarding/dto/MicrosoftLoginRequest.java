package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotBlank;

/** The Microsoft ID token the browser obtained through MSAL. */
public record MicrosoftLoginRequest(
        @NotBlank(message = "A Microsoft sign-in token is required") String idToken
) {
}
