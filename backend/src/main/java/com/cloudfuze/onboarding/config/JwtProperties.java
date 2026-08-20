package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC signing secret. Must be at least 32 characters. Supplied via environment. */
    private String secret = "";

    private String issuer = "cloudfuze-onboarding";

    private Duration expiration = Duration.ofHours(8);
}
