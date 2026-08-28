package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.config.AzureAdProperties;
import com.cloudfuze.onboarding.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates a Microsoft Entra ID (Azure AD) ID token that the browser obtained
 * through MSAL, and pulls out who signed in.
 *
 * <p>Trust is anchored on three checks: the signature must verify against the
 * tenant's published keys, the issuer must be this exact tenant, and the
 * audience must be our own application. Anything else is rejected before a
 * session is ever minted.
 *
 * <p>Created only when Microsoft sign-in is configured; without a tenant id the
 * bean is absent and the endpoint that needs it is not exposed.
 */
@Component
@ConditionalOnProperty(name = "security.azure.tenant-id")
public class MicrosoftTokenVerifier {

    private final JwtDecoder decoder;

    public MicrosoftTokenVerifier(AzureAdProperties properties) {
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();

        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(properties.getClientId()));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audience));
        this.decoder = nimbus;
    }

    public MicrosoftIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MICROSOFT_TOKEN_INVALID",
                    "That Microsoft sign-in could not be verified. Please try again.");
        }

        // Azure puts the sign-in address in preferred_username; email is a fallback.
        String email = jwt.getClaimAsString("preferred_username");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("email");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MICROSOFT_TOKEN_NO_EMAIL",
                    "That Microsoft account did not return an email address.");
        }

        String name = jwt.getClaimAsString("name");
        String oid = jwt.getClaimAsString("oid");
        return new MicrosoftIdentity(email.trim(), name == null ? email.trim() : name.trim(), oid);
    }

    public record MicrosoftIdentity(String email, String displayName, String objectId) {
    }
}
