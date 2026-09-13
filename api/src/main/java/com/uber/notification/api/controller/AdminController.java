package com.uber.notification.api.controller;

import com.uber.notification.api.security.JwtService;
import com.uber.notification.application.usecase.AdminDashboardUseCase;
import com.uber.notification.application.usecase.BroadcastNotificationUseCase;
import com.uber.notification.domain.model.NotificationPriority;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.infrastructure.realtime.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin dashboard APIs: platform stats, broadcast, and delivery metrics.
 * Restricted to ROLE_ADMIN (URL + method level).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminDashboardUseCase dashboardUseCase;
    private final BroadcastNotificationUseCase broadcastUseCase;
    private final NotificationRepository notificationRepository;
    private final WebSocketSessionRegistry sessionRegistry;

    public AdminController(AdminDashboardUseCase dashboardUseCase,
                           BroadcastNotificationUseCase broadcastUseCase,
                           NotificationRepository notificationRepository,
                           WebSocketSessionRegistry sessionRegistry) {
        this.dashboardUseCase = dashboardUseCase;
        this.broadcastUseCase = broadcastUseCase;
        this.notificationRepository = notificationRepository;
        this.sessionRegistry = sessionRegistry;
    }

    public record AdminStatsResponse(
            long totalNotifications,
            long totalUsers,
            long notificationsToday,
            long unreadNotifications,
            double readRatePercentage) {
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        var s = dashboardUseCase.stats();
        return new AdminStatsResponse(s.totalNotifications(), s.totalUsers(), s.notificationsToday(),
                s.unreadNotifications(), s.readRatePercentage());
    }

    public record BroadcastRequest(
            @NotBlank String title,
            @NotBlank String message,
            NotificationPriority priority) {
    }

    @PostMapping("/broadcast")
    public Map<String, Integer> broadcast(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                          @Valid @RequestBody BroadcastRequest request) {
        int delivered = broadcastUseCase.broadcast(request.title(), request.message(),
                request.priority() == null ? NotificationPriority.MEDIUM : request.priority());
        return Map.of("delivered", delivered);
    }

    @GetMapping("/delivery-metrics")
    public Map<String, Long> deliveryMetrics(
            @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        Map<String, Long> metrics = new LinkedHashMap<>();
        for (NotificationStatus status : NotificationStatus.values()) {
            metrics.put(status.name(), notificationRepository.countByStatus(status));
        }
        return metrics;
    }

    @GetMapping("/active-sessions")
    public Map<String, Integer> activeSessions(
            @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        // Per-pod local session count is also exposed via the websocket.sessions.active
        // Micrometer gauge; this endpoint reports the same value for dashboard polling.
        return Map.of("activeWebSocketSessions", sessionRegistry.activeSessionCount());
    }
}
