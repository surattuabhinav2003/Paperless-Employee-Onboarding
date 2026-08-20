package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.repository.HrUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Authenticates HR requests from the Bearer token. Candidate portal requests are
 * deliberately untouched here - they authenticate with their portal token inside
 * the portal service instead.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final HrUserRepository hrUserRepository;

    public JwtAuthenticationFilter(JwtService jwtService, HrUserRepository hrUserRepository) {
        this.jwtService = jwtService;
        this.hrUserRepository = hrUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/portal/") || path.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(header.substring(BEARER.length()).trim()).ifPresent(claims -> {
                try {
                    UUID userId = UUID.fromString(claims.getSubject());
                    hrUserRepository.findById(userId)
                            .filter(user -> user.isActive())
                            .map(HrPrincipal::new)
                            .ifPresent(principal -> {
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        principal, null, principal.getAuthorities());
                                authentication.setDetails(
                                        new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            });
                } catch (IllegalArgumentException ignored) {
                    // Malformed subject - request continues unauthenticated.
                }
            });
        }
        filterChain.doFilter(request, response);
    }
}
