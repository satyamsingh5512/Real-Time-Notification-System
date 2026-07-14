package com.uber.notification.common.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffCalculatorTest {

    @Test
    void rejectsNonPositiveAttemptNumber() {
        assertThatThrownBy(() -> BackoffCalculator.computeDelay(0, Duration.ofSeconds(1), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @RepeatedTest(20)
    void delayNeverExceedsCap() {
        Duration cap = Duration.ofSeconds(30);
        Duration delay = BackoffCalculator.computeDelay(10, Duration.ofSeconds(1), cap);
        assertThat(delay).isLessThanOrEqualTo(cap);
        assertThat(delay.isNegative()).isFalse();
    }

    @Test
    void delayGrowsWithAttemptNumberOnAverage() {
        Duration base = Duration.ofSeconds(1);
        Duration cap = Duration.ofMinutes(10);

        long attempt1Avg = averageDelayMillis(1, base, cap, 200);
        long attempt5Avg = averageDelayMillis(5, base, cap, 200);

        assertThat(attempt5Avg).isGreaterThan(attempt1Avg);
    }

    private long averageDelayMillis(int attempt, Duration base, Duration cap, int samples) {
        long total = 0;
        for (int i = 0; i < samples; i++) {
            total += BackoffCalculator.computeDelay(attempt, base, cap).toMillis();
        }
        return total / samples;
    }
}
