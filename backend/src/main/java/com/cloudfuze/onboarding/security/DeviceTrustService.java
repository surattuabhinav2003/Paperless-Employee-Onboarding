package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.config.JwtProperties;
import com.cloudfuze.onboarding.config.PortalTokenProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Remembers that a candidate has already proved control of their inbox on this
 * device, so they are asked for a code once rather than on every visit.
 *
 * <p>The marker is a signed string, not a database row: {@code candidateId.expiry.hmac}.
 * Nothing secret is inside it and it cannot be forged without the server key, so
 * there is no session table to grow or clean up. Losing the key simply asks
 * everyone for a code again, which is the safe failure.
 */
@Service
public class DeviceTrustService {

    /** Long enough that a candidate finishing over a week is not re-challenged. */
    private static final Duration TRUST_FOR = Duration.ofDays(30);
    private static final String HMAC = "HmacSHA256";

    private final byte[] key;

    public DeviceTrustService(PortalTokenProperties portalTokenProperties, JwtProperties jwtProperties) {
        String secret = portalTokenProperties.getEncryptionSecret();
        if (secret == null || secret.isBlank()) {
            secret = jwtProperties.getSecret();
        }
        this.key = ("device-trust:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    /** A marker the candidate's browser keeps; presenting it skips the code. */
    public String issue(UUID candidateId) {
        long expiresAt = Instant.now().plus(TRUST_FOR).getEpochSecond();
        String payload = candidateId + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    /**
     * True only if the marker is well formed, unexpired, signed by this server,
     * and issued for this candidate - so one candidate's marker can never be
     * replayed against another's portal.
     */
    public boolean isTrusted(String marker, UUID candidateId) {
        if (marker == null || marker.isBlank()) {
            return false;
        }
        String[] parts = marker.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        String payload = parts[0] + "." + parts[1];

        // Constant-time: a byte-by-byte comparison leaks where a forgery diverges.
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        if (!parts[0].equals(candidateId.toString())) {
            return false;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(parts[1])).isAfter(Instant.now());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign a device marker", e);
        }
    }
}
