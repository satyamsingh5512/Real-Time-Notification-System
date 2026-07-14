package com.uber.notification.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs and provides a Redis-backed distributed lock for the scheduler. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final String REGISTRY_KEY = "notification-platform-scheduler";

    @Bean
    public LockRegistry lockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, REGISTRY_KEY, 30_000);
    }
}
