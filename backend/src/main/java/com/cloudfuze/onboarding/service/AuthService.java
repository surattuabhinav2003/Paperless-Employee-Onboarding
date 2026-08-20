package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.HrUserDto;
import com.cloudfuze.onboarding.dto.LoginRequest;
import com.cloudfuze.onboarding.dto.LoginResponse;
import com.cloudfuze.onboarding.exception.ApiException;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final HrUserRepository hrUserRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, HrUserRepository hrUserRepository,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.hrUserRepository = hrUserRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            HrPrincipal principal = (HrPrincipal) authentication.getPrincipal();

            hrUserRepository.findById(principal.getId()).ifPresent(user -> {
                user.setLastLoginAt(Instant.now());
                hrUserRepository.save(user);
            });

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

    @Transactional(readOnly = true)
    public HrUserDto currentUser(HrPrincipal principal) {
        return hrUserRepository.findById(principal.getId())
                .map(HrUserDto::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID",
                        "Your session is no longer valid. Please sign in again."));
    }
}
