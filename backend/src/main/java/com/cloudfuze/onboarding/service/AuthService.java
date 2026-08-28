package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.HrUserDto;
import com.cloudfuze.onboarding.dto.LoginRequest;
import com.cloudfuze.onboarding.dto.LoginResponse;
import com.cloudfuze.onboarding.exception.ApiException;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.config.AzureAdProperties;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.security.JwtService;
import com.cloudfuze.onboarding.security.MicrosoftTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final HrUserRepository hrUserRepository;
    private final JwtService jwtService;

    private final com.cloudfuze.onboarding.security.RateLimiter rateLimiter;

    /* Present only when Microsoft sign-in is configured; the verifier bean is
       conditional on a tenant id, so it is optional here. */
    private final Optional<MicrosoftTokenVerifier> microsoftTokenVerifier;
    private final AzureAdProperties azureProperties;
    private final PasswordEncoder passwordEncoder;
    private final com.cloudfuze.onboarding.config.AppProperties appProperties;

    public AuthService(AuthenticationManager authenticationManager, HrUserRepository hrUserRepository,
                       JwtService jwtService,
                       com.cloudfuze.onboarding.security.RateLimiter rateLimiter,
                       Optional<MicrosoftTokenVerifier> microsoftTokenVerifier,
                       AzureAdProperties azureProperties,
                       PasswordEncoder passwordEncoder,
                       com.cloudfuze.onboarding.config.AppProperties appProperties) {
        this.authenticationManager = authenticationManager;
        this.hrUserRepository = hrUserRepository;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.microsoftTokenVerifier = microsoftTokenVerifier;
        this.azureProperties = azureProperties;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        /*
         * Per-account limiting, on top of the per-IP limit in RateLimitFilter.
         * A botnet spreads guesses across thousands of addresses, so a per-IP
         * cap alone never sees an attack on one account.
         */
        String accountKey = com.cloudfuze.onboarding.security.RateLimitFilter.accountKey(email);
        if (!rateLimiter.tryConsume(accountKey,
                com.cloudfuze.onboarding.security.RateLimitFilter.accountLimit(),
                com.cloudfuze.onboarding.security.RateLimitFilter.accountWindow())) {
            long wait = rateLimiter.retryAfterSeconds(accountKey);
            log.warn("Sign-in temporarily locked for {}", email);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_TEMPORARILY_LOCKED",
                    "Too many failed sign-in attempts. Try again in " + Math.max(1, wait / 60) + " minute(s).");
        }

        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            HrPrincipal principal = (HrPrincipal) authentication.getPrincipal();

            hrUserRepository.findById(principal.getId()).ifPresent(user -> {
                user.setLastLoginAt(Instant.now());
                hrUserRepository.save(user);
            });

            /* A correct password clears the counter, so one mistyped attempt
               never counts against a legitimate user later in the day. */
            rateLimiter.reset(accountKey);

            JwtService.IssuedToken issued = jwtService.issue(principal);
            HrUser user = hrUserRepository.findById(principal.getId()).orElseThrow();
            log.info("HR user {} signed in", email);
            return new LoginResponse(issued.token(), "Bearer", issued.expiresInSeconds(), issued.expiresAt(),
                    HrUserDto.from(user));
        } catch (AuthenticationException e) {
            log.warn("Failed sign-in attempt for {}", email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Those credentials do not match our records.");
        }
    }

    /**
     * Signs an HR user in from a Microsoft Entra ID token.
     *
     * <p>The token is validated against the tenant's keys, the address is checked
     * against the HR allow-list, and a first-time signer is provisioned an HR
     * record (with an unusable password, so the email/password path can never be
     * used for an SSO account). From there it mints the same session token as a
     * password sign-in, so the rest of the app is unchanged.
     */
    public LoginResponse loginWithMicrosoft(String idToken) {
        MicrosoftTokenVerifier verifier = microsoftTokenVerifier.orElseThrow(() ->
                new ApiException(HttpStatus.NOT_IMPLEMENTED, "MICROSOFT_LOGIN_DISABLED",
                        "Microsoft sign-in is not configured on this server."));

        MicrosoftTokenVerifier.MicrosoftIdentity identity = verifier.verify(idToken);
        String email = identity.email().toLowerCase(Locale.ROOT);

        // Being on the console already is its own authorisation: an admin who
        // adds someone on the People & access screen has said yes just as much
        // as the configured allow-list does, and should not need a config
        // change and a restart to make it stick.
        Optional<HrUser> existing = hrUserRepository.findByEmailIgnoreCase(email);
        if (existing.isEmpty() && !azureProperties.allows(email)) {
            log.warn("Microsoft sign-in refused for {} - not on the HR allow-list", email);
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_AUTHORIZED_HR",
                    "This Microsoft account is not set up as an HR user. Contact your administrator.");
        }

        HrUser user = existing.orElseGet(() -> findOrProvision(email, identity.displayName()));

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "HR_ACCOUNT_DISABLED",
                    "This HR account has been disabled.");
        }

        // A person added by an admin was stored under a placeholder name taken
        // from their address. Their first sign-in is the first time we hear the
        // real one, so take it - but only that once, never overwriting a name
        // someone has since been recorded under.
        if (user.getLastLoginAt() == null && identity.displayName() != null
                && !identity.displayName().isBlank()) {
            user.setFullName(identity.displayName());
        }

        user.setLastLoginAt(Instant.now());
        hrUserRepository.save(user);

        JwtService.IssuedToken issued = jwtService.issue(new HrPrincipal(user));
        log.info("HR user {} signed in with Microsoft", email);
        return new LoginResponse(issued.token(), "Bearer", issued.expiresInSeconds(), issued.expiresAt(),
                HrUserDto.from(user));
    }

    /**
     * Finds the HR user for this address, creating one on a first Microsoft
     * sign-in. Not wrapped in one transaction on purpose: if two sign-ins race to
     * create the same new user, the loser catches the unique-key violation and
     * simply reads the row the winner just wrote.
     */
    private HrUser findOrProvision(String email, String displayName) {
        return hrUserRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            if (!azureProperties.isAutoProvision()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_AUTHORIZED_HR",
                        "This Microsoft account is not set up as an HR user. Contact your administrator.");
            }
            // A random, unknowable password: satisfies the NOT NULL column while
            // making the email/password path impossible for an SSO-only account.
            String unusable = passwordEncoder.encode("sso:" + UUID.randomUUID());
            HrUser fresh = new HrUser(email, unusable, displayName, azureProperties.getDefaultJobTitle());
            // Someone on the admin list gets it from their very first sign-in,
            // rather than needing a restart to be promoted.
            if (appProperties.getSeed().isAdminEmail(email)) {
                fresh.setRole(com.cloudfuze.onboarding.model.HrRole.ADMIN);
            }
            try {
                HrUser saved = hrUserRepository.save(fresh);
                log.info("Provisioned HR user {} on first Microsoft sign-in", email);
                return saved;
            } catch (DataIntegrityViolationException race) {
                // A concurrent sign-in created it first; use that record.
                return hrUserRepository.findByEmailIgnoreCase(email).orElseThrow(() -> race);
            }
        });
    }

    @Transactional(readOnly = true)
    public HrUserDto currentUser(HrPrincipal principal) {
        return hrUserRepository.findById(principal.getId())
                .map(HrUserDto::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID",
                        "Your session is no longer valid. Please sign in again."));
    }
}
