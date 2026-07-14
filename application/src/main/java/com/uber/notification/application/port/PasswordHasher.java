package com.uber.notification.application.port;

/** Output port for hashing/verifying credentials, implemented with BCrypt in infrastructure. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
