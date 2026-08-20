package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.config.JwtProperties;
import com.cloudfuze.onboarding.config.PortalTokenProperties;
import com.cloudfuze.onboarding.exception.InvalidPortalTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Candidate portal tokens.
 * <p>
 * A token is 32 bytes of {@link SecureRandom} output, URL-safe base64 encoded.
 * Two derivatives are persisted, and never the token itself:
 * <ul>
 *   <li>a SHA-256 hash, used to look a candidate up from an incoming link, so a
 *       database dump cannot be replayed against the portal;</li>
 *   <li>an AES-GCM ciphertext, so HR can re-read a link that is still valid.
 *       The key lives in configuration rather than the database, so the
 *       ciphertext is inert on its own.</li>
 * </ul>
 * Regenerating a token overwrites both, which immediately invalidates the
 * previous link.
 */
@Service
public class PortalTokenService {

    private static final Logger log = LoggerFactory.getLogger(PortalTokenService.class);

    private static final int TOKEN_BYTES = 32;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final AppProperties appProperties;
    private final SecretKey encryptionKey;

    public PortalTokenService(AppProperties appProperties, PortalTokenProperties tokenProperties,
                             JwtProperties jwtProperties) {
        this.appProperties = appProperties;
        String secret = tokenProperties.getEncryptionSecret();
        if (secret == null || secret.isBlank()) {
            // Derived, so no extra configuration is needed to run; set
            // security.portal-token.encryption-secret to rotate it separately.
            secret = "portal-token:" + jwtProperties.getSecret();
        }
        this.encryptionKey = deriveKey(secret);
    }

    /** Generates a fresh token. The raw value is returned for immediate use. */
    public IssuedPortalToken issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(appProperties.getPortalTokenTtl());
        return new IssuedPortalToken(raw, hash(raw), encrypt(raw), now, expiresAt,
                appProperties.buildPortalUrl(raw));
    }

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidPortalTokenException();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", e);
        }
    }

    public String encrypt(String rawToken) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Could not protect the portal token", e);
        }
    }

    /**
     * Recovers a stored token. Empty when there is nothing to recover, or when the
     * ciphertext cannot be read with the current key - the caller then offers to
     * generate a new link rather than failing outright.
     */
    public Optional<String> decrypt(String storedCipher) {
        if (storedCipher == null || storedCipher.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedCipher);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return Optional.of(new String(decrypted, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Stored portal token could not be read - the encryption secret may have changed: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    public String portalUrl(String rawToken) {
        return appProperties.buildPortalUrl(rawToken);
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", e);
        }
    }

    public record IssuedPortalToken(
            String rawToken,
            String tokenHash,
            String tokenCipher,
            Instant issuedAt,
            Instant expiresAt,
            String portalUrl
    ) {
    }
}
