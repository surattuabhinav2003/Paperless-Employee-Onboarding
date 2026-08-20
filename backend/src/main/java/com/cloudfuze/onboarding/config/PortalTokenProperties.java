package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Protection for the recoverable copy of a candidate portal token.
 * <p>
 * The lookup value stays a one-way SHA-256 hash. Alongside it the token is kept
 * encrypted so HR can re-read an active link instead of invalidating it just to
 * see it. Leave {@code encryption-secret} blank to derive the key from the JWT
 * secret, or set it to rotate the two independently.
 */
@ConfigurationProperties(prefix = "security.portal-token")
@Getter
@Setter
public class PortalTokenProperties {

    private String encryptionSecret = "";
}
