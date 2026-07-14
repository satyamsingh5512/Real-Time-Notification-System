package com.uber.notification.api.controller;

import com.uber.notification.api.dto.notification.NotificationResponse;
import com.uber.notification.api.security.JwtService;
import com.uber.notification.application.usecase.NotificationHistoryUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification inbox API: paginated history, unread count, mark read/unread, and soft delete.
 * All endpoints operate on the authenticated caller's own notifications; the use case layer
 * enforces ownership so a user cannot read or mutate another user's notifications (IDOR).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationHistoryUseCase historyUseCase;

    public NotificationController(NotificationHistoryUseCase historyUseCase) {
        this.historyUseCase = historyUseCase;
    }

    @GetMapping
    public List<NotificationResponse> getHistory(
            @AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return historyUseCase.getHistory(principal.userId(), includeDeleted, page, Math.min(size, 100)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return Map.of("unreadCount", historyUseCase.getUnreadCount(principal.userId()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                          @PathVariable UUID id) {
        return NotificationResponse.from(historyUseCase.markRead(id, principal.userId()));
    }

    @PatchMapping("/{id}/unread")
    public NotificationResponse markUnread(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                            @PathVariable UUID id) {
        return NotificationResponse.from(historyUseCase.markUnread(id, principal.userId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public void softDelete(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                            @PathVariable UUID id) {
        historyUseCase.softDelete(id, principal.userId());
    }
}
