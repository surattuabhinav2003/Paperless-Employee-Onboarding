package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.security.RateLimitFilter;
import com.cloudfuze.onboarding.security.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The integration suite runs with rate limiting switched off - it signs in once
 * per test, which is exactly the pattern the limiter exists to stop. These cover
 * the limiter itself instead.
 */
class RateLimiterTest {

    @Test
    @DisplayName("Allows up to the limit, then refuses")
    void allowsUpToTheLimit() {
        RateLimiter limiter = new RateLimiter();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(limiter.tryConsume("k", 5, Duration.ofMinutes(15)))
                    .as("attempt %d should be allowed", attempt)
                    .isTrue();
        }
        assertThat(limiter.tryConsume("k", 5, Duration.ofMinutes(15))).isFalse();
        assertThat(limiter.tryConsume("k", 5, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    @DisplayName("Keys are independent, so one account cannot lock out another")
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("login:account:a@example.com", 5, Duration.ofMinutes(15));
        }

        assertThat(limiter.tryConsume("login:account:a@example.com", 5, Duration.ofMinutes(15))).isFalse();
        assertThat(limiter.tryConsume("login:account:b@example.com", 5, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    @DisplayName("The window expires, so a lockout is temporary")
    void windowExpires() {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryConsume("k", 1, Duration.ofMillis(40))).isTrue();
        assertThat(limiter.tryConsume("k", 1, Duration.ofMillis(40))).isFalse();

        await(60);

        assertThat(limiter.tryConsume("k", 1, Duration.ofMillis(40)))
                .as("a fresh window should start once the old one lapses")
                .isTrue();
    }

    @Test
    @DisplayName("A successful sign-in clears the counter")
    void resetClearsTheCounter() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("k", 5, Duration.ofMinutes(15));
        }
        assertThat(limiter.tryConsume("k", 5, Duration.ofMinutes(15))).isFalse();

        limiter.reset("k");

        assertThat(limiter.tryConsume("k", 5, Duration.ofMinutes(15)))
                .as("one mistyped password should not count against a user all day")
                .isTrue();
    }

    @Test
    @DisplayName("Retry-After is a positive number of seconds while locked")
    void reportsRetryAfter() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryConsume("k", 1, Duration.ofMinutes(15));

        assertThat(limiter.retryAfterSeconds("k")).isBetween(1L, 900L);
        assertThat(limiter.retryAfterSeconds("never-seen")).isZero();
    }

    @Test
    @DisplayName("Account keys are normalised, so casing cannot dodge the limit")
    void accountKeysAreNormalised() {
        assertThat(RateLimitFilter.accountKey("  Admin@CloudFuze.com "))
                .isEqualTo(RateLimitFilter.accountKey("admin@cloudfuze.com"));
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
