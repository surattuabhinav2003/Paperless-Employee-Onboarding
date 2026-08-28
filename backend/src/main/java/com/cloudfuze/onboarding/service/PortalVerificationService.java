package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.config.JwtProperties;
import com.cloudfuze.onboarding.config.PortalTokenProperties;
import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.exception.ApiException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.security.DeviceTrustService;
import com.cloudfuze.onboarding.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Proves the person holding a portal link also controls the mailbox it was sent
 * to, by mailing a short code.
 *
 * <p>The link alone is already a credential, so this exists to catch the case
 * where a link is forwarded, pasted into a shared channel, or read from a device
 * that is not the candidate's. Once verified the device is remembered for 30
 * days, so an ordinary candidate sees this once.
 */
@Service
public class PortalVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PortalVerificationService.class);

    private static final Duration CODE_VALID_FOR = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESENDS_PER_HOUR = 5;
    /* The email is not secret, but it is a second factor next to the link, so a
       holder of the link cannot simply guess their way to the candidate's. */
    private static final int EMAIL_ATTEMPTS_PER_HOUR = 10;
    private static final String HMAC = "HmacSHA256";

    private final CandidateRepository candidateRepository;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final DeviceTrustService deviceTrustService;
    private final AuditService auditService;
    private final RateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public PortalVerificationService(CandidateRepository candidateRepository, EmailService emailService,
                                     EmailProperties emailProperties, DeviceTrustService deviceTrustService,
                                     AuditService auditService, RateLimiter rateLimiter,
                                     PortalTokenProperties portalTokenProperties, JwtProperties jwtProperties) {
        this.candidateRepository = candidateRepository;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
        this.deviceTrustService = deviceTrustService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        String secret = portalTokenProperties.getEncryptionSecret();
        if (secret == null || secret.isBlank()) {
            secret = jwtProperties.getSecret();
        }
        this.key = ("portal-otp:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    /** What the code screen needs: who it went to, without exposing the address. */
    public record Challenge(String maskedEmail, int digits, long expiresInSeconds) {
    }

    /**
     * Confirms the candidate typed the email this link was sent to, then issues a
     * code (or reuses the live one).
     *
     * <p>Requiring the email first means a forwarded or pasted link is not enough
     * on its own: the person opening it must also know which address it belongs
     * to. The check is rate-limited so the link cannot be used to guess the
     * address by trial.
     *
     * <p>Reuse of a live code matters twice over: a candidate who clicks resend
     * while the first mail is still arriving would otherwise invalidate the code
     * they are about to type, and every extra send costs real money.
     */
    @Transactional
    public Challenge requestCode(Candidate candidate, String enteredEmail, String ipAddress) {
        String emailKey = "otp:email:" + candidate.getId();
        if (!rateLimiter.tryConsume(emailKey, EMAIL_ATTEMPTS_PER_HOUR, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many attempts. Please wait a while before trying again.");
        }
        String entered = enteredEmail == null ? "" : enteredEmail.trim();
        if (!candidate.getEmail().equalsIgnoreCase(entered)) {
            auditService.recordCandidateEvent(candidate.getId(), AuditEventType.VERIFICATION_FAILED,
                    candidate.getEmail(), ipAddress, null, Map.of("reason", "email_mismatch"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_MISMATCH",
                    "That email doesn't match this invitation. Enter the email your invitation was sent to.");
        }
        // A correct email should not count against the candidate's own resends.
        rateLimiter.reset(emailKey);

        String limitKey = "otp:candidate:" + candidate.getId();
        if (!rateLimiter.tryConsume(limitKey, RESENDS_PER_HOUR, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_CODES",
                    "Too many codes requested. Please wait a while before trying again.");
        }

        Instant now = Instant.now();
        boolean live = candidate.getOtpHash() != null
                && candidate.getOtpExpiresAt() != null
                && candidate.getOtpExpiresAt().isAfter(now)
                && candidate.getOtpAttempts() < MAX_ATTEMPTS;

        String code = null;
        if (!live) {
            code = String.format("%06d", random.nextInt(1_000_000));
            candidate.setOtpHash(hash(code));
            candidate.setOtpExpiresAt(now.plus(CODE_VALID_FOR));
            candidate.setOtpAttempts(0);
            candidate.setOtpSentAt(now);
            candidateRepository.save(candidate);
        }

        if (code != null) {
            // Off the request thread: the candidate should see the code screen
            // immediately rather than waiting on the mail server.
            sendCode(candidate.getEmail(), candidate.getName(), code);
            auditService.recordSystemEvent(candidate.getId(), AuditEventType.VERIFICATION_CODE_SENT,
                    Map.of("email", mask(candidate.getEmail()), "ip", ipAddress == null ? "unknown" : ipAddress));
        }

        long remaining = Duration.between(now, candidate.getOtpExpiresAt()).getSeconds();
        return new Challenge(mask(candidate.getEmail()), 6, Math.max(remaining, 1));
    }

    /**
     * Checks a code and, on success, returns a device marker.
     *
     * <p>A wrong code burns an attempt. After {@value #MAX_ATTEMPTS} the code is
     * destroyed rather than merely rejected, so guessing cannot continue against
     * the same code.
     */
    @Transactional
    public String verify(Candidate candidate, String submitted, String ipAddress) {
        Instant now = Instant.now();

        if (candidate.getOtpHash() == null || candidate.getOtpExpiresAt() == null
                || !candidate.getOtpExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_EXPIRED",
                    "That code has expired. Please ask for a new one.");
        }
        if (candidate.getOtpAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many incorrect codes. Please ask for a new one.");
        }

        String cleaned = submitted == null ? "" : submitted.replaceAll("\\D", "");
        // Constant-time: comparing digit by digit would leak how much is right.
        boolean ok = MessageDigest.isEqual(
                hash(cleaned).getBytes(StandardCharsets.UTF_8),
                candidate.getOtpHash().getBytes(StandardCharsets.UTF_8));

        if (!ok) {
            candidate.setOtpAttempts(candidate.getOtpAttempts() + 1);
            if (candidate.getOtpAttempts() >= MAX_ATTEMPTS) {
                candidate.setOtpHash(null);
                candidate.setOtpExpiresAt(null);
            }
            candidateRepository.save(candidate);
            auditService.recordCandidateEvent(candidate.getId(), AuditEventType.VERIFICATION_FAILED,
                    candidate.getEmail(), ipAddress, null,
                    Map.of("attempt", candidate.getOtpAttempts()));
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_INCORRECT",
                    "That code is not right. " + (MAX_ATTEMPTS - candidate.getOtpAttempts())
                            + " attempt(s) left.");
        }

        candidate.setOtpHash(null);
        candidate.setOtpExpiresAt(null);
        candidate.setOtpAttempts(0);
        candidate.setLastVerifiedAt(now);
        candidateRepository.save(candidate);
        rateLimiter.reset("otp:candidate:" + candidate.getId());

        auditService.recordCandidateEvent(candidate.getId(), AuditEventType.VERIFICATION_SUCCEEDED,
                candidate.getEmail(), ipAddress, null, Map.of());
        log.info("Candidate {} verified their email", candidate.getEmail());

        return deviceTrustService.issue(candidate.getId());
    }

    public boolean isTrusted(String marker, Candidate candidate) {
        return deviceTrustService.isTrusted(marker, candidate.getId());
    }

    @Async
    void sendCode(String toAddress, String toName, String code) {
        String subject = code + " is your Neutara onboarding code";
        String text = """
                Hello %s,

                Your Neutara onboarding verification code is:

                    %s

                It expires in 10 minutes. If you did not ask for this code, you can
                ignore this email - your onboarding link stays safe.

                Neutara People Operations
                """.formatted(toName, code);

        String html = """
                <div style="font-family:Segoe UI,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto">
                  <p style="font-size:15px;color:#0b1533">Hello %s,</p>
                  <p style="font-size:14px;color:#46536e">Your Neutara onboarding verification code is:</p>
                  <p style="font-family:'Courier New',monospace;font-size:34px;font-weight:700;
                            letter-spacing:10px;color:#234297;margin:24px 0">%s</p>
                  <p style="font-size:13px;color:#6b7688">It expires in 10 minutes.</p>
                  <p style="font-size:13px;color:#6b7688">If you did not ask for this code you can ignore
                     this email - your onboarding link stays safe.</p>
                  <p style="font-size:13px;color:#46536e">Neutara People Operations</p>
                </div>
                """.formatted(toName, code);

        try {
            /* Never blind-copied. This code is the second factor proving the
               candidate controls their own inbox; the portal link is already in
               another email the archive recipient receives, so copying this one
               too would hand them a working key to anyone's documents. */
            emailService.send(new EmailMessage(toAddress, toName, subject, text, html).notArchivable());
        } catch (RuntimeException e) {
            // Never fail the request because mail is down; the candidate can resend.
            log.error("Could not send a verification code to {}", mask(toAddress), e);
        }
    }

    /** a***@example.com - enough to recognise, not enough to harvest. */
    public static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "your email";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String shown = name.isEmpty() ? "" : name.substring(0, 1);
        return shown + "***@" + parts[1];
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash a verification code", e);
        }
    }
}
