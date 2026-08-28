package com.cloudfuze.onboarding.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window counter, keyed by whatever the caller decides identifies an
 * abuser - an IP, an email address, or both.
 *
 * <p>Deliberately in-memory and dependency-free: this guards a single instance,
 * which is what the app runs as today. Behind more than one instance the windows
 * become per-instance and the effective limit multiplies, so a shared store
 * (Redis) would be needed - see {@code SECURITY.md}.
 *
 * <p>Windows are counted, not leaky-bucketed, because the failure mode that
 * matters here is a burst of guesses, not a steady trickle.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /** Entries older than this are swept, so a long-running process cannot grow unbounded. */
    private static final Duration SWEEP_AFTER = Duration.ofHours(1);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant resetAt, AtomicInteger count) {
    }

    /**
     * Records one hit against {@code key} and reports whether it is allowed.
     *
     * @param limit  hits permitted inside the window
     * @param window how long the window lasts
     */
    public boolean tryConsume(String key, int limit, Duration window) {
        sweepOccasionally();
        Instant now = Instant.now();

        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.resetAt().isBefore(now)) {
                return new Window(now.plus(window), new AtomicInteger(0));
            }
            return existing;
        });

        int used = current.count().incrementAndGet();
        if (used > limit) {
            log.warn("Rate limit hit for {} ({} of {} in window)", key, used, limit);
            return false;
        }
        return true;
    }

    /** Seconds until {@code key}'s window resets, for a Retry-After header. */
    public long retryAfterSeconds(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), window.resetAt()).getSeconds();
        return Math.max(seconds, 1);
    }

    /** Clears a key - used when a sign-in succeeds, so one bad typo is not punished. */
    public void reset(String key) {
        windows.remove(key);
    }

    private void sweepOccasionally() {
        if (windows.size() < 10_000) {
            return;
        }
        Instant cutoff = Instant.now().minus(SWEEP_AFTER);
        windows.entrySet().removeIf(entry -> entry.getValue().resetAt().isBefore(cutoff));
    }
}
