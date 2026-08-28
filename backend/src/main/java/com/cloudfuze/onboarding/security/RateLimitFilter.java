package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Throttles the endpoints an attacker would actually hammer.
 *
 * <p>Three of them matter:
 * <ul>
 *   <li><b>sign-in</b> - the only password in the system, so it is limited per IP
 *       <em>and</em> per account. Per-account matters because a botnet spreads
 *       guesses across many addresses, which a per-IP limit alone never sees.</li>
 *   <li><b>the candidate portal</b> - the URL token is the credential. 32 bytes of
 *       {@code SecureRandom} is not brute-forceable, but a limit turns a
 *       pointless flood into a cheap rejection.</li>
 *   <li><b>invitation resends</b> - each one sends real mail through the
 *       account's SMTP credentials, so an unbounded loop burns sending
 *       reputation and could be used to mailbomb a candidate.</li>
 * </ul>
 *
 * <p>Runs before authentication so unauthenticated floods are rejected without
 * touching the database.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    /* Defaults are generous enough that a real HR user never notices, and
       tight enough that automated guessing is pointless. */
    private static int loginPerAccount = 5;
    private static Duration loginWindow = Duration.ofMinutes(15);

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int loginPerIp;
    private final int portalPerIp;
    private final Duration portalWindow;
    private final int sendPerIp;
    private final Duration sendWindow;

    public RateLimitFilter(
            RateLimiter limiter,
            ObjectMapper objectMapper,
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.rate-limit.login-per-ip:10}") int loginPerIp,
            @Value("${security.rate-limit.login-per-account:5}") int loginPerAccountValue,
            @Value("${security.rate-limit.login-window:15m}") Duration loginWindowValue,
            @Value("${security.rate-limit.portal-per-ip:120}") int portalPerIp,
            @Value("${security.rate-limit.portal-window:1m}") Duration portalWindow,
            @Value("${security.rate-limit.send-per-ip:20}") int sendPerIp,
            @Value("${security.rate-limit.send-window:1h}") Duration sendWindow) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.loginPerIp = loginPerIp;
        this.portalPerIp = portalPerIp;
        this.portalWindow = portalWindow;
        this.sendPerIp = sendPerIp;
        this.sendWindow = sendWindow;
        loginPerAccount = loginPerAccountValue;
        loginWindow = loginWindowValue;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String ip = clientIp(request);
        String key = null;

        if ("POST".equals(request.getMethod()) && path.equals("/api/auth/login")) {
            key = "login:ip:" + ip;
            if (!limiter.tryConsume(key, loginPerIp, loginWindow)) {
                reject(request, response, key);
                return;
            }
        } else if (path.startsWith("/api/portal/")) {
            key = "portal:ip:" + ip;
            if (!limiter.tryConsume(key, portalPerIp, portalWindow)) {
                reject(request, response, key);
                return;
            }
        } else if ("POST".equals(request.getMethod())
                && (path.endsWith("/resend-invite") || path.endsWith("/regenerate-token"))) {
            key = "send:ip:" + ip;
            if (!limiter.tryConsume(key, sendPerIp, sendWindow)) {
                reject(request, response, key);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** Per-account sign-in limiting, called by the auth service once the email is known. */
    public static String accountKey(String email) {
        return "login:account:" + (email == null ? "" : email.trim().toLowerCase());
    }

    public static int accountLimit() {
        return loginPerAccount;
    }

    public static Duration accountWindow() {
        return loginWindow;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String key)
            throws IOException {
        long retryAfter = limiter.retryAfterSeconds(key);
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(429, "TOO_MANY_REQUESTS",
                "Too many attempts. Please wait " + retryAfter + " seconds and try again.",
                request.getRequestURI()));
    }

    /*
     * Only trusts a proxy header when one is present; behind a load balancer the
     * first entry is the client. Without a proxy this is the socket address.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
