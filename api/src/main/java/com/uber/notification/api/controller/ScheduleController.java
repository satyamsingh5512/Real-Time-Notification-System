package com.uber.notification.api.controller;

import com.uber.notification.api.dto.notification.NotificationResponse;
import com.uber.notification.api.dto.schedule.ScheduleNotificationRequest;
import com.uber.notification.application.usecase.ScheduleNotificationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal/service-to-service API for scheduling delayed notifications (e.g. "remind user
 * in 24h if cart not checked out"). Restricted to SERVICE/ADMIN roles since it's meant to
 * be called by other backend services, not end users directly.
 */
@RestController
@RequestMapping("/api/v1/internal/notifications/schedule")
@PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
public class ScheduleController {

    private final ScheduleNotificationUseCase scheduleNotificationUseCase;

    public ScheduleController(ScheduleNotificationUseCase scheduleNotificationUseCase) {
        this.scheduleNotificationUseCase = scheduleNotificationUseCase;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> schedule(@Valid @RequestBody ScheduleNotificationRequest request) {
        var notification = scheduleNotificationUseCase.schedule(
                request.userId(), request.eventType(), request.channel(),
                request.templateCode(), request.payload(), request.scheduledFor());
        return ResponseEntity.status(HttpStatus.CREATED).body(NotificationResponse.from(notification));
    }
}
