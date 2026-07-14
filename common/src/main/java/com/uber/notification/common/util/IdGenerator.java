package com.uber.notification.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** ULID-like sortable ID generator: millis-timestamp prefix + random suffix, but kept as UUID for JPA simplicity. */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }

    public static String newIdempotencyKey(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli() + "-" + Math.abs(RANDOM.nextLong());
    }
}
