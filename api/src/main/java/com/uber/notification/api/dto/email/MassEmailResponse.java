package com.uber.notification.api.dto.email;

/**
 * Result of a campaign preview or send.
 *
 * <p>{@code dryRun} and {@code blocked} are the two fields an admin needs first:
 * a preview reports {@code targeted} without sending anything, and {@code blocked} carries
 * the machine-readable reason a send was refused rather than failing the request.
 */
public record MassEmailResponse(
        String campaignId,
        boolean dryRun,
        int scanned,
        int targeted,
        int skippedOptedOut,
        int sent,
        int failed,
        boolean truncated,
        Boolean blocked,
        String blockedReason,
        Integer quotaLimitPerDay,
        Integer quotaUsedToday,
        String message
) {

    public static MassEmailResponse blocked(String reason, Integer limit, Integer used) {
        return new MassEmailResponse(null, false, 0, 0, 0, 0, 0, false,
                true, reason, limit, used,
                "Campaign not sent: " + reason);
    }
}
