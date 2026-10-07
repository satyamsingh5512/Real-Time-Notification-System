package com.uber.notification.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Spec-compliant health surface that wraps the Actuator probes:
 * <ul>
 *   <li>{@code GET /health} — process alive (never touches the DB).</li>
 *   <li>{@code GET /health/live} — liveness (process alive).</li>
 *   <li>{@code GET /health/ready} — readiness (DB reachable + Flyway migrations applied).</li>
 * </ul>
 * Liveness intentionally avoids PostgreSQL so orchestrators don't restart a healthy
 * process during a transient DB blip; readiness gates traffic instead.
 */
@RestController
@Tag(name = "Health", description = "Liveness / readiness probes for Docker, K8s and deploy scripts")
public class HealthController {

    private final org.springframework.beans.factory.ObjectProvider<DataSource> dataSourceProvider;
    private final org.springframework.beans.factory.ObjectProvider<ApplicationAvailability> availabilityProvider;

    public HealthController(org.springframework.beans.factory.ObjectProvider<DataSource> dataSourceProvider,
                            org.springframework.beans.factory.ObjectProvider<ApplicationAvailability> availabilityProvider) {
        this.dataSourceProvider = dataSourceProvider;
        this.availabilityProvider = availabilityProvider;
    }

    @GetMapping("/health")
    @Operation(summary = "Liveness: process is alive")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "notification-platform"));
    }

    @GetMapping("/health/live")
    @Operation(summary = "Kubernetes-style liveness")
    public ResponseEntity<Map<String, Object>> live() {
        ApplicationAvailability availability = availabilityProvider.getIfAvailable();
        if (availability == null) {
            return ResponseEntity.ok(Map.of("status", "UP", "liveness", "CORRECT"));
        }
        LivenessState state = availability.getLivenessState();
        if (state == LivenessState.BROKEN) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "liveness", state.name()));
        }
        return ResponseEntity.ok(Map.of("status", "UP", "liveness", state.name()));
    }

    @GetMapping("/health/ready")
    @Operation(summary = "Readiness: DB + migrations reachable")
    public ResponseEntity<Map<String, Object>> ready() {
        ApplicationAvailability availability = availabilityProvider.getIfAvailable();
        String readiness = availability == null ? "ACCEPTING_TRAFFIC" : availability.getReadinessState().name();
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return ResponseEntity.ok(Map.of("status", "UP", "readiness", readiness));
        }
        try {
            Integer one = new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                throw new IllegalStateException("DB probe returned unexpected value");
            }
            // Flyway gate: notifications table must exist (V1 applied).
            new JdbcTemplate(dataSource).queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'notifications'", Long.class);
            return ResponseEntity.ok(Map.of("status", "UP",
                    "readiness", readiness, "database", "UP"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }
}
