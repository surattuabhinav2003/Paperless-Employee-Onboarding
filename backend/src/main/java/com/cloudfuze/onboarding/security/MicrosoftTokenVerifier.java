package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.config.AzureAdProperties;
import com.cloudfuze.onboarding.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    private static final Logger log = LoggerFactory.getLogger(MicrosoftTokenVerifier.class);

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

    /**
     * Fetches Microsoft's signing keys once at startup, off the request path.
     *
     * <p>The decoder holds those keys in a cache that is empty until something
     * asks it to verify a token, so the first person to sign in with Microsoft
     * after a restart waits on a round trip to Microsoft - measured at about
     * 300ms from here - before their own sign-in is even looked at. Doing it
     * here moves that cost to a moment when nobody is waiting.
     *
     * <p>There is no "just fetch the keys" call on the decoder, so this asks it
     * to verify a token shaped correctly but signed by nobody. It is rejected,
     * as it must be; populating the key cache on the way is the point. On its
     * own thread, because a slow or unreachable Microsoft must delay startup no
     * more than it already delays the first sign-in.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmSigningKeys() {
        CompletableFuture.runAsync(() -> {
            long startedAt = System.currentTimeMillis();
            try {
                decoder.decode(unsignedProbeToken());
            } catch (Exception expected) {
                // Rejected, as designed. The keys are what we came for.
            }
            log.debug("Microsoft signing keys warmed in {}ms", System.currentTimeMillis() - startedAt);
        });
    }

    /** A syntactically valid JWS that no key will ever verify. */
    private static String unsignedProbeToken() {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String header = base64.encodeToString(
                "{\"alg\":\"RS256\",\"kid\":\"warm-up\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".AA";
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
