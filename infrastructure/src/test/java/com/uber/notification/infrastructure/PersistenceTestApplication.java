package com.uber.notification.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Boot application used only to bootstrap a Spring context for infrastructure-module
 * integration tests (JPA repository adapters against a real Postgres Testcontainer). Kafka
 * and Redis autoconfiguration are excluded since these tests only exercise the persistence
 * layer and would otherwise require live brokers.
 */
@SpringBootApplication(exclude = {KafkaAutoConfiguration.class, RedisAutoConfiguration.class})
@ComponentScan(basePackages = "com.uber.notification.infrastructure.persistence")
@EnableJpaRepositories(basePackages = "com.uber.notification.infrastructure.persistence.jpa")
public class PersistenceTestApplication {
}
