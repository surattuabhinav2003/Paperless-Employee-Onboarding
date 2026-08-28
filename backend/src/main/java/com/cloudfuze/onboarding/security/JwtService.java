package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and validates the HR access tokens. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_LENGTH = 32;

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("security.jwt.secret must be set to at least " + MIN_SECRET_LENGTH
                    + " characters (configure JWT_SECRET in the environment).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(HrPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getExpiration());
        String token = Jwts.builder()
                .subject(principal.getId().toString())
                .issuer(properties.getIssuer())
                .claim("email", principal.getEmail())
                .claim("name", principal.getFullName())
                .claim("scope", "hr")
                .claim("role", principal.getRole().getCode())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt, properties.getExpiration().toSeconds());
    }

    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
