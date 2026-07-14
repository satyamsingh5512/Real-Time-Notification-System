package com.uber.notification.api.controller;

import com.uber.notification.api.dto.template.CreateTemplateRequest;
import com.uber.notification.api.dto.template.TemplateResponse;
import com.uber.notification.application.usecase.ManageNotificationTemplateUseCase;
import com.uber.notification.domain.model.NotificationChannel;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only API for managing versioned notification templates (task: Notification Templates engine). */
@RestController
@RequestMapping("/api/v1/admin/templates")
@PreAuthorize("hasRole('ADMIN')")
public class TemplateController {

    private final ManageNotificationTemplateUseCase templateUseCase;

    public TemplateController(ManageNotificationTemplateUseCase templateUseCase) {
        this.templateUseCase = templateUseCase;
    }

    @PostMapping
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request) {
        return TemplateResponse.from(templateUseCase.createNewVersion(
                request.code(), request.channel(), request.locale() != null ? request.locale() : "en-US",
                request.subjectTemplate(), request.bodyTemplate()));
    }

    @GetMapping("/{code}")
    public List<TemplateResponse> getVersionHistory(@PathVariable String code) {
        return templateUseCase.getVersionHistory(code).stream().map(TemplateResponse::from).toList();
    }

    @GetMapping("/{code}/active")
    public TemplateResponse getActive(@PathVariable String code, @RequestParam NotificationChannel channel) {
        return TemplateResponse.from(templateUseCase.getActive(code, channel));
    }
}
