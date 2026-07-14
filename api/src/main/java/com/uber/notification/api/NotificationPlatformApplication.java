package com.uber.notification.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.uber.notification")
public class NotificationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationPlatformApplication.class, args);
    }
}
