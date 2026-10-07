package com.uber.notification.api.dto.email;

import com.uber.notification.domain.model.NotificationPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Admin request to preview or send a mass-email campaign.
 *
 * <p>{@code subject} and {@code body} accept the same {@code {{placeholder}}} syntax as
 * notification templates ({@code TemplateRenderer}), so an admin can address each
 * recipient by name from their own record.
 */
public record MassEmailRequest(

        @NotBlank
        @Size(max = 200)
        String subject,

        @NotBlank
        @Size(max = 20_000)
        String body,

        /** Values substituted into {@code {{placeholders}}} in subject and body. */
        Map<String, String> variables,

        NotificationPriority priority,

        /**
         * Optional idempotency key. Re-sending with the same id is a no-op per recipient
         * — the action that matters most for an irreversible, outward-facing operation.
         * Omitted, a fresh campaign is created.
         */
        @Size(max = 100)
        String campaignId
) {
    public String campaignId() {
        return campaignId == null || campaignId.isBlank() ? null : campaignId.trim();
    }

    public NotificationPriority priorityOrDefault() {
        return priority == null ? NotificationPriority.MEDIUM : priority;
    }
}
