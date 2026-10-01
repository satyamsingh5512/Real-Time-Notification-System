package com.uber.notification.api.controller;

import com.uber.notification.api.dto.preference.ChannelOptInRequest;
import com.uber.notification.api.dto.preference.DigestCadenceRequest;
import com.uber.notification.api.dto.preference.FrequencyCapsRequest;
import com.uber.notification.api.dto.preference.IntentRequest;
import com.uber.notification.api.dto.preference.PreferenceResponse;
import com.uber.notification.api.dto.preference.QuietHoursRequest;
import com.uber.notification.api.security.JwtService;
import com.uber.notification.application.usecase.ManageUserPreferenceUseCase;
import com.uber.notification.domain.model.EventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Notification Preferences API. Intent (whether to notify) is separate from
 * delivery (how): {@code PUT /{eventType}/intent} controls generation, while
 * channel/quiet-hours/push/caps/digest control delivery and batching.
 */
@RestController
@RequestMapping("/api/v1/preferences")
@Tag(name = "Preferences", description = "Per-event notification intent, channel opt-in, quiet hours, "
        + "push frequency caps and digest cadence")
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

    @PutMapping("/{eventType}/intent")
    @Operation(summary = "Set whether this event type generates notifications (ALL / MENTIONS / MUTE)")
    public PreferenceResponse setIntent(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                         @PathVariable EventType eventType,
                                         @Valid @RequestBody IntentRequest request) {
        return PreferenceResponse.from(
                preferenceUseCase.setIntent(principal.userId(), eventType, request.intent()));
    }

    @PutMapping("/{eventType}/push")
    @Operation(summary = "Enable or disable push delivery for this event type")
    public PreferenceResponse setPushEnabled(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                             @PathVariable EventType eventType,
                                             @RequestParam boolean enabled) {
        return PreferenceResponse.from(
                preferenceUseCase.setPushEnabled(principal.userId(), eventType, enabled));
    }

    @PutMapping("/{eventType}/frequency-caps")
    @Operation(summary = "Set ATC-style push frequency caps (daily budget + minimum gap between pushes)")
    public PreferenceResponse setFrequencyCaps(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                               @PathVariable EventType eventType,
                                               @Valid @RequestBody FrequencyCapsRequest request) {
        return PreferenceResponse.from(preferenceUseCase.setFrequencyCaps(
                principal.userId(), eventType, request.maxPushesPerDay(), request.minHoursBetweenPushes()));
    }

    @PutMapping("/{eventType}/digest")
    @Operation(summary = "Set digest cadence for low-value email (OFF / DAILY / WEEKLY)")
    public PreferenceResponse setDigestCadence(@AuthenticationPrincipal JwtService.AuthenticatedPrincipal principal,
                                               @PathVariable EventType eventType,
                                               @Valid @RequestBody DigestCadenceRequest request) {
        return PreferenceResponse.from(
                preferenceUseCase.setDigestCadence(principal.userId(), eventType, request.cadence()));
    }
}
