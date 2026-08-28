package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.HrUserDto;
import com.cloudfuze.onboarding.dto.LoginRequest;
import com.cloudfuze.onboarding.dto.LoginResponse;
import com.cloudfuze.onboarding.dto.MicrosoftLoginRequest;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HR authentication. Sign-out is a client-side token discard; there is no
 * server session to invalidate.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Exchanges a Microsoft (Entra ID) token for an app session. */
    @PostMapping("/microsoft")
    public ResponseEntity<LoginResponse> microsoft(@Valid @RequestBody MicrosoftLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithMicrosoft(request.idToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<HrUserDto> me(@AuthenticationPrincipal HrPrincipal principal) {
        return ResponseEntity.ok(authService.currentUser(principal));
    }
}
