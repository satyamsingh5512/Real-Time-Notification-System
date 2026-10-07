package com.uber.notification.application.usecase;

import com.uber.notification.application.template.TemplateRenderer;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationIntent;
import com.uber.notification.domain.model.NotificationPriority;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.domain.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mass-email campaign fan-out (admin only).
 *
 * <p>Modelled on {@link BroadcastNotificationUseCase}, with two deliberate differences:
 *
 * <ol>
 *   <li><b>The channel is EMAIL, not IN_APP.</b> Email leaves the building, so each
 *       recipient's own preference is consulted first. A user who switched EMAIL off, or
 *       muted any event type, is not targeted.</li>
 *   <li><b>Delivery goes through {@link DeliverNotificationUseCase}</b>, one recipient at a
 *       time, rather than a single POST to the email service's {@code /api/email/bulk}.
 *       Bulk would be faster but bypasses the circuit breaker, retry topics, dead-letter
 *       routing and delivery metrics — one bad address would take out the whole campaign
 *       invisibly.</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> the admin may supply a {@code campaignId}. Re-running with the
 * same id is a no-op per recipient, because the idempotency key
 * ({@code mass-email:<campaignId>:<userId>}) is already on a row and the database's
 * unique constraint rejects a duplicate. Without this, re-POSTing an identical campaign
 * double-sent it — a real hazard for an action that sends mail to real people.
 */
public class MassEmailUseCase {

    /** Recipients scanned per repository page, to keep memory bounded on large installs. */
    private static final int PAGE_SIZE = 200;

    /**
     * Hard ceiling on one run. The API layer's configurable recipient cap is normally
     * lower; this is the backstop that bounds the work regardless of configuration.
     */
    static final int MAX_PER_RUN = 1_000;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final DeliverNotificationUseCase deliverNotificationUseCase;

    public MassEmailUseCase(NotificationRepository notificationRepository,
                            UserRepository userRepository,
                            UserPreferenceRepository preferenceRepository,
                            DeliverNotificationUseCase deliverNotificationUseCase) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
    }

    /**
     * Outcome of one campaign run.
     *
     * @param alreadySent recipients skipped because this campaign id already targeted
     *                    them — the idempotency guard firing
     */
    public record CampaignResult(
            String campaignId,
            int scanned,
            int targeted,
            int skippedOptedOut,
            int alreadySent,
            int sent,
            int failed,
            boolean truncated
    ) {
        /** Recipients this run added and attempted. */
        public int totalAttempted() {
            return sent + failed;
        }
    }

    /**
     * Dry run: counts how many users a campaign would reach without sending anything, so
     * an admin can see the blast radius — and how much of it opted out — before
     * committing.
     */
    public CampaignResult preview(String campaignId, String subjectTemplate, String bodyTemplate,
                                  Map<String, String> variables, NotificationPriority priority) {
        return run(campaignId, subjectTemplate, bodyTemplate, variables, priority, true);
    }

    /**
     * Live send.
     *
     * @param campaignId admin-supplied id for idempotency; a fresh one is generated when
     *                   null, which makes the run a distinct campaign
     */
    public CampaignResult send(String campaignId, String subjectTemplate, String bodyTemplate,
                               Map<String, String> variables, NotificationPriority priority) {
        return run(campaignId, subjectTemplate, bodyTemplate, variables, priority, false);
    }

    private CampaignResult run(String requestedCampaignId, String subjectTemplate, String bodyTemplate,
                               Map<String, String> variables, NotificationPriority priority,
                               boolean dryRun) {
        String campaignId = requestedCampaignId == null || requestedCampaignId.isBlank()
                ? IdGenerator.newId().toString()
                : requestedCampaignId.trim();
        Map<String, String> payload = variables == null ? Map.of() : variables;

        int scanned = 0;
        int targeted = 0;
        int optedOut = 0;
        int alreadySent = 0;
        int sent = 0;
        int failed = 0;
        boolean truncated = false;

        int page = 0;
        while (true) {
            List<UUID> userIds = userRepository.findAllIds(page, PAGE_SIZE);
            if (userIds.isEmpty()) {
                break;
            }
            for (UUID userId : userIds) {
                // The cap is checked before anything is counted, so `scanned` always
                // equals `targeted + skippedOptedOut`. Checking afterwards made the
                // reported audience wrong by one on every truncated response, and burned a
                // preference read on a user that was then discarded.
                if (targeted >= MAX_PER_RUN) {
                    truncated = true;
                    break;
                }

                scanned++;
                if (!mayReceiveEmail(userId)) {
                    optedOut++;
                    continue;
                }
                targeted++;

                String idempotencyKey = "mass-email:" + campaignId + ":" + userId;
                if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
                    // Already delivered under this campaign id. Re-running must not resend.
                    alreadySent++;
                    continue;
                }

                if (dryRun) {
                    continue;
                }

                Notification notification = new Notification(
                        IdGenerator.newId(), userId, EventType.BROADCAST_ANNOUNCEMENT,
                        NotificationChannel.EMAIL, "MASS_EMAIL",
                        payload, 3, null, Instant.now(), idempotencyKey);
                notification.setPriority(priority);
                // Rendered here rather than by the delivery use case: a campaign's copy is
                // authored in the request, not resolved from a versioned template row.
                // DeliverNotificationUseCase now skips re-rendering when content is
                // already present, which is what makes this the effective copy.
                notification.markRendered(
                        TemplateRenderer.render(subjectTemplate, payload),
                        TemplateRenderer.render(bodyTemplate, payload));

                try {
                    notificationRepository.save(notification);
                    deliverNotificationUseCase.execute(notification);

                    // DeliverNotificationUseCase records failures on the row and does not
                    // rethrow, so an exception here means something went wrong *before*
                    // delivery was attempted. Reading the row status is the only way to
                    // know what actually happened — counting the absence of an exception
                    // reported every campaign as fully sent, even when every message
                    // dead-lettered.
                    if (notification.getStatus() == NotificationStatus.SENT) {
                        sent++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    // Includes the unique-constraint violation if offset pagination ever
                    // yields the same user twice. One recipient must never abort the run.
                    failed++;
                }
            }

            if (truncated) {
                break;
            }
            if (userIds.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        return new CampaignResult(campaignId, scanned, targeted, optedOut,
                alreadySent, sent, failed, truncated);
    }

    /**
     * Whether this user may receive campaign email.
     *
     * <p>Absent preference rows mean "no explicit choice", which the domain already models
     * as opted-in. A campaign is suppressed if the user has muted <em>any</em> event type
     * or switched EMAIL off for <em>any</em> event type — not just for the broadcast event.
     * A user who muted ORDER_PLACED has told us they do not want email, and mailing them
     * anyway because the row is keyed by a different event type is exactly the behaviour
     * that gets a sender blocked.
     *
     * <p><b>Product decision:</b> this also excludes a user who muted a security event type
     * (e.g. PASSWORD_RESET). Suppressing marketing mail from someone who muted a security
     * alert is defensible — they are cautious — and erring toward not emailing is the
     * right default.
     *
     * <p><b>Cost:</b> one preference read per candidate, so a run is O(users). Acceptable
     * while mass-mail is disabled by default and capped; a batched
     * {@code findAllByUserIdIn} port is the fix if this ever runs against a large base.
     */
    private boolean mayReceiveEmail(UUID userId) {
        return preferenceRepository.findAllByUserId(userId).stream().noneMatch(MassEmailUseCase::blocks);
    }

    private static boolean blocks(UserPreference preference) {
        return preference.getIntent() == NotificationIntent.MUTE
                || !preference.isChannelEnabled(NotificationChannel.EMAIL);
    }

    /** Exposed so the API layer can report the ceiling before doing any work. */
    public static int maxPerRun() {
        return MAX_PER_RUN;
    }
}