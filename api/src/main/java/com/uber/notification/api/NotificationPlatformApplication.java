package com.uber.notification.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.uber.notification")
@EnableJpaRepositories(basePackages = "com.uber.notification.infrastructure.persistence.jpa")
@EntityScan(basePackages = "com.uber.notification.infrastructure.persistence.entity")
public class NotificationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationPlatformApplication.class, args);
    }
}
