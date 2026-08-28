package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Two independent authentication paths:
 * <ul>
 *   <li>HR APIs ({@code /api/hr/**}) require a valid JWT.</li>
 *   <li>Candidate APIs ({@code /api/portal/**}) are open to the filter chain and
 *       authenticate with the hashed portal token inside the service layer, which
 *       also enforces expiry and stage gating.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public SecurityConfig(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                /*
                 * No CSRF token is correct here and only here: the app is stateless
                 * and authenticates from the Authorization header, never a cookie.
                 * If a token ever moves into a cookie this must come back.
                 */
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentTypeOptions(opts -> {})
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        /*
                         * The candidate's portal token lives in the URL path, so a
                         * referrer header would leak the credential to any site they
                         * click through to.
                         */
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        /*
                         * The API returns JSON and streams uploads; it never needs to
                         * execute script or be framed. Locking it down means a stored
                         * file that slipped through validation still cannot run.
                         */
                        .addHeaderWriter((request, response) -> {
                            response.setHeader("Content-Security-Policy",
                                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; "
                                            + "form-action 'none'; sandbox");
                            response.setHeader("Cross-Origin-Resource-Policy", "same-site");
                            response.setHeader("Permissions-Policy",
                                    "camera=(), microphone=(), geolocation=(), interest-cohort=()");
                        }))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/microsoft", "/api/auth/session-check").permitAll()
                        .requestMatchers("/api/portal/**").permitAll()
                        /* NDA + NOC recipients are not users of the app: the link's
                           token is the credential, hashed and expiry-checked in the
                           service exactly as portal tokens are. */
                        .requestMatchers("/api/noc/**").permitAll()
                        .requestMatchers("/api/meta/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/hr/admin/**").hasAuthority(HrPrincipal.ADMIN_ROLE)
                        .requestMatchers("/api/hr/**").hasAuthority(HrPrincipal.ROLE)
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> write(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                                "Your session has expired. Please sign in again.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) -> write(response,
                                HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                                "You do not have permission to perform this action.", request.getRequestURI())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(HrUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider::authenticate;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void write(HttpServletResponse response, int status, String code, String message, String path) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, code, message, path));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
