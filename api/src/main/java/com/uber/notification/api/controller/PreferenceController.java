package com.uber.notification.api.controller;

import com.uber.notification.api.dto.preference.ChannelOptInRequest;
import com.uber.notification.api.dto.preference.PreferenceResponse;
import com.uber.notification.api.dto.preference.QuietHoursRequest;
import com.uber.notification.api.security.JwtService;
import com.uber.notification.application.usecase.ManageUserPreferenceUseCase;
import com.uber.notification.domain.model.EventType;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Notification Preferences CRUD API: per-event-type channel opt-in/out and quiet hours. */
@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {

    private final ManageUserPreferenceUseCase preferenceUseCase;

    public PreferenceController(ManageUserPreferenceUseCase preferenceUseCase) {
        this.preferenceUseCase = preferenceUseCase;
    }

    @GetMapping
    public List<PreferenceResponse> getAll(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal) {
        return preferenceUseCase.getAllForUser(principal.userId()).stream()
                .map(PreferenceResponse::from)
                .toList();
    }

    @PutMapping("/{eventType}/channel")
    public PreferenceResponse setChannelOptIn(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                               @PathVariable EventType eventType,
                                               @Valid @RequestBody ChannelOptInRequest request) {
        return PreferenceResponse.from(preferenceUseCase.setChannelOptIn(
                principal.userId(), eventType, request.channel(), request.enabled()));
    }

    @PutMapping("/{eventType}/quiet-hours")
    public PreferenceResponse setQuietHours(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                             @PathVariable EventType eventType,
                                             @Valid @RequestBody QuietHoursRequest request) {
        return PreferenceResponse.from(preferenceUseCase.setQuietHours(
                principal.userId(), eventType, request.enabled(), request.startHour(), request.endHour()));
    }

    @DeleteMapping("/{eventType}")
    public void delete(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                        @PathVariable EventType eventType) {
        preferenceUseCase.deletePreference(principal.userId(), eventType);
    }
}
