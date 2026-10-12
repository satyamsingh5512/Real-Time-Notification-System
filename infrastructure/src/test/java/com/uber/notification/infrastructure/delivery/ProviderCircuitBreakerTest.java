package com.uber.notification.infrastructure.delivery;

import com.uber.notification.domain.model.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCircuitBreakerTest {

    @Test
    void allowsUntilThresholdThenTrips() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(3, 60, null);

        assertThat(breaker.allow(NotificationChannel.EMAIL)).isTrue();
        breaker.recordFailure(NotificationChannel.EMAIL, true);
        breaker.recordFailure(NotificationChannel.EMAIL, true);
        assertThat(breaker.allow(NotificationChannel.EMAIL)).isTrue(); // 2 < 3

        breaker.recordFailure(NotificationChannel.EMAIL, true);
        assertThat(breaker.allow(NotificationChannel.EMAIL)).isFalse(); // tripped
    }

    @Test
    void permanentFailuresNeverTripTheBreaker() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(2, 60, null);

        for (int i = 0; i < 10; i++) {
            breaker.recordFailure(NotificationChannel.SMS, false); // invalid number, not throttling
        }

        assertThat(breaker.allow(NotificationChannel.SMS)).isTrue();
    }

    @Test
    void successResetsTheFailureRun() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(3, 60, null);

        breaker.recordFailure(NotificationChannel.PUSH, true);
        breaker.recordFailure(NotificationChannel.PUSH, true);
        breaker.recordSuccess(NotificationChannel.PUSH);
        breaker.recordFailure(NotificationChannel.PUSH, true);

        assertThat(breaker.allow(NotificationChannel.PUSH)).isTrue(); // run restarted
    }

    @Test
    void breakersAreIsolatedPerChannel() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(2, 60, null);

        breaker.recordFailure(NotificationChannel.EMAIL, true);
        breaker.recordFailure(NotificationChannel.EMAIL, true);
        assertThat(breaker.allow(NotificationChannel.EMAIL)).isFalse();

        assertThat(breaker.allow(NotificationChannel.WEBSOCKET)).isTrue();
    }

    @Test
    void halfOpenAfterCooldownAllowsASingleProbe() throws Exception {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(1, 1, null);

        breaker.recordFailure(NotificationChannel.EMAIL, true);
        assertThat(breaker.allow(NotificationChannel.EMAIL)).isFalse();

        Thread.sleep(1100);

        assertThat(breaker.allow(NotificationChannel.EMAIL)).isTrue(); // half-open probe
        breaker.recordSuccess(NotificationChannel.EMAIL);
        assertThat(breaker.allow(NotificationChannel.EMAIL)).isTrue(); // closed again
    }
}