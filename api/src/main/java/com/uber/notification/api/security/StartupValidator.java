package com.uber.notification.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fail-fast production guard: refuses to boot when required secrets are missing
 * or still set to documented development placeholders. Development defaults in
 * {@code application.yml} are intentionally weak so this check is the enforcement
 * point (rather than breaking local `bootRun` for every contributor).
 */
@Component
public class StartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private static final Set<String> FORBIDDEN_JWT_SECRETS = Set.of(
            "",
            "CHANGE_ME",
            "CHANGE_ME_IN_PRODUCTION_use_a_32+_byte_random_secret",
            "local-dev-secret-change-me-please-32-bytes-min");

    private final String jwtSecret;
    private final String appEnv;

    public StartupValidator(
            @Value("${security.jwt.secret:}") String jwtSecret,
            @Value("${APP_ENV:${SPRING_PROFILES_ACTIVE:local}}") String appEnv) {
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret.trim();
        this.appEnv = appEnv;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = appEnv.contains("prod") || appEnv.contains("docker") && isProdLike();
        if (jwtSecret.length() < 32 || FORBIDDEN_JWT_SECRETS.contains(jwtSecret)) {
            if (prod) {
                throw new IllegalStateException(
                        "Refusing to start: JWT_SECRET must be set to 32+ random bytes in production. "
                                + "Generate with: openssl rand -base64 48");
            }
            log.warn("JWT_SECRET is a development placeholder — never use this value in production.");
        }
    }

    private boolean isProdLike() {
        String jwtEnv = System.getenv("JWT_SECRET");
        // docker compose without an explicit .env still injects the placeholder; treat
        // an explicitly-provided secret as intentional.
        return jwtEnv != null && !jwtEnv.isBlank() && !FORBIDDEN_JWT_SECRETS.contains(jwtEnv.trim());
    }
}
