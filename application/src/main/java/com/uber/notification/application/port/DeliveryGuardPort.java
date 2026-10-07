package com.uber.notification.application.port;

import com.uber.notification.domain.model.NotificationChannel;

/**
 * Per-provider circuit breaker (SES/Twilio/FCM 429 + outage protection).
 * Implemented in infrastructure; consulted by the delivery use case so a
 * struggling provider fails fast into the retry path instead of burning
 * threads and worsening the throttle.
 */
public interface DeliveryGuardPort {

    /** Whether a send attempt to this channel may proceed right now. */
    boolean allow(NotificationChannel channel);

    void recordSuccess(NotificationChannel channel);

    void recordFailure(NotificationChannel channel, boolean retryable);

    DeliveryGuardPort ALLOW_ALL = new DeliveryGuardPort() {
        @Override
        public boolean allow(NotificationChannel channel) {
            return true;
        }

        @Override
        public void recordSuccess(NotificationChannel channel) {
        }

        @Override
        public void recordFailure(NotificationChannel channel, boolean retryable) {
        }
    };
}
