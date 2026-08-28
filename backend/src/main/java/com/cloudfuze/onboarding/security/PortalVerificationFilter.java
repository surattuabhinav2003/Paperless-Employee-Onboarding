package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.exception.ErrorResponse;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.service.PortalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires a verified device for every candidate endpoint.
 *
 * <p>Enforced centrally rather than in each controller method: the portal has a
 * dozen endpoints and several authenticate inside the service layer, so a
 * per-method check is one refactor away from being silently dropped. A filter
 * cannot be forgotten.
 *
 * <p>The two verification endpoints are exempt - they are how a device becomes
 * trusted - as is the document stream, which is fetched by the in-app viewer
 * with the same header.
 */
@Component
@Order(2)
public class PortalVerificationFilter extends OncePerRequestFilter {

    private final PortalService portalService;
    private final DeviceTrustService deviceTrustService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public PortalVerificationFilter(@Lazy PortalService portalService,
                                    DeviceTrustService deviceTrustService,
                                    ObjectMapper objectMapper,
                                    @Value("${security.portal-verification.enabled:true}") boolean enabled) {
        this.portalService = portalService;
        this.deviceTrustService = deviceTrustService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!enabled || !path.startsWith("/api/portal/") || path.contains("/verify")) {
            chain.doFilter(request, response);
            return;
        }

        String token = tokenFrom(path);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        Candidate candidate;
        try {
            candidate = portalService.authenticate(token);
        } catch (RuntimeException e) {
            // A bad or expired link is not this filter's problem; let the normal
            // handler produce its own message.
            chain.doFilter(request, response);
            return;
        }

        if (deviceTrustService.isTrusted(request.getHeader(PortalService.DEVICE_HEADER), candidate.getId())) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(403, "VERIFICATION_REQUIRED",
                "Please confirm the code we emailed you before continuing.", path));
    }

    /** /api/portal/{token}/... -> token */
    private static String tokenFrom(String path) {
        String rest = path.substring("/api/portal/".length());
        int slash = rest.indexOf('/');
        String token = slash < 0 ? rest : rest.substring(0, slash);
        return token.isBlank() ? null : token;
    }
}
