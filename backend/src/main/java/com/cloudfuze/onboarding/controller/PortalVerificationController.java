package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.service.PortalService;
import com.cloudfuze.onboarding.service.PortalVerificationService;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email verification for the candidate portal.
 *
 * <p>These two endpoints are deliberately outside the device-trust gate - they
 * are how a candidate passes it. Everything else in the portal requires either a
 * trusted device or a completed verification.
 */
@RestController
@RequestMapping("/api/portal/{token}/verify")
public class PortalVerificationController {

    private final PortalService portalService;
    private final PortalVerificationService verificationService;

    public PortalVerificationController(PortalService portalService,
                                        PortalVerificationService verificationService) {
        this.portalService = portalService;
        this.verificationService = verificationService;
    }

    public record SendCodeRequest(@NotBlank(message = "Enter your email") @Email(message = "Enter a valid email")
                              String email) {
    }

    public record VerifyRequest(@NotBlank(message = "Enter the code from your email") String code) {
    }

    /** What the code screen shows, plus whether this device is already trusted. */
    public record ChallengeResponse(String candidateName, String maskedEmail, int digits,
                                    long expiresInSeconds, boolean alreadyTrusted) {
    }

    public record VerifiedResponse(String deviceToken) {
    }

    /**
     * Checks the candidate typed the right email for this link, then sends a code -
     * or reports that this device is already trusted and needs neither.
     */
    @PostMapping("/request")
    public ResponseEntity<ChallengeResponse> request(@PathVariable String token,
                                                     @Valid @RequestBody SendCodeRequest body,
                                                     HttpServletRequest httpRequest) {
        Candidate candidate = portalService.authenticate(token);
        String marker = httpRequest.getHeader(PortalService.DEVICE_HEADER);

        if (verificationService.isTrusted(marker, candidate)) {
            return ResponseEntity.ok(new ChallengeResponse(candidate.getName(),
                    PortalVerificationService.mask(candidate.getEmail()), 6, 0, true));
        }

        var challenge = verificationService.requestCode(candidate, body.email(),
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(new ChallengeResponse(candidate.getName(), challenge.maskedEmail(),
                challenge.digits(), challenge.expiresInSeconds(), false));
    }

    @PostMapping
    public ResponseEntity<VerifiedResponse> verify(@PathVariable String token,
                                                   @RequestBody VerifyRequest body,
                                                   HttpServletRequest httpRequest) {
        Candidate candidate = portalService.authenticate(token);
        String deviceToken = verificationService.verify(candidate, body.code(),
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(new VerifiedResponse(deviceToken));
    }
}
