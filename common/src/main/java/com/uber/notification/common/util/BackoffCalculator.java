package com.uber.notification.common.util;

import java.time.Duration;

/**
 * Pure, deterministic exponential backoff calculator with jitter.
 * Used by both the Kafka retry-topic scheduler and any in-process retry logic,
 * so the algorithm lives in `common` rather than being duplicated.
 */
public final class BackoffCalculator {

    private BackoffCalculator() {
    }

    /**
     * Computes delay before attempt {@code attemptNumber} (1-indexed) using
     * full-jitter exponential backoff: delay = random(0, min(cap, base * 2^(attempt-1))).
     */
    public static Duration computeDelay(int attemptNumber, Duration base, Duration cap) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        long baseMs = base.toMillis();
        long capMs = cap.toMillis();
        long exp = (long) (baseMs * Math.pow(2, attemptNumber - 1));
        long boundedMs = Math.min(capMs, exp);
        long jittered = (long) (Math.random() * boundedMs);
        return Duration.ofMillis(Math.max(jittered, baseMs / 2));
    }
}
