package com.uber.notification.api.controller;

import com.uber.notification.api.dto.email.MassEmailRequest;
import com.uber.notification.api.dto.email.MassEmailResponse;
import com.uber.notification.application.usecase.MassEmailUseCase;
import com.uber.notification.common.exception.ValidationException;
import com.uber.notification.infrastructure.provider.email.EmailServiceProperties;
import com.uber.notification.infrastructure.provider.email.EmailServiceQuotaClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mass-email campaign APIs (ROLE_ADMIN).
 *
 * <p>Delivers through the external email service
 * (github.com/satyamsingh5512/email-service) via the platform's ordinary EMAIL channel,
 * so per-recipient opt-in, the circuit breaker, retry topics, dead-letter routing and
 * delivery metrics all apply.
 *
 * <h2>Two guards, both deliberate</h2>
 * <ol>
 *   <li><b>Disabled by default.</b> Requires {@code notification.email-service.mass-mail-enabled=true}.
 *       Mass unsolicited email is the highest-abuse-risk action in this product, and the
 *       upstream service's 100/day quota makes it non-functional at scale regardless.</li>
 *   <li><b>Preview before send.</b> {@code /preview} returns the blast radius — how many
 *       users would be reached and how many opted out — without sending. An admin should
 *       not have to guess at audience size before sending real mail to real people.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/admin/email")
@PreAuthorize("hasRole('ADMIN')")
public class MassEmailController {

    private static final Logger log = LoggerFactory.getLogger(MassEmailController.class);

    private final MassEmailUseCase massEmailUseCase;
    private final ObjectProvider<EmailServiceQuotaClient> quotaClient;
    private final ObjectProvider<EmailServiceProperties> properties;

    public MassEmailController(MassEmailUseCase massEmailUseCase,
                               ObjectProvider<EmailServiceQuotaClient> quotaClient,
                               ObjectProvider<EmailServiceProperties> properties) {
        this.massEmailUseCase = massEmailUseCase;
        this.quotaClient = quotaClient;
        this.properties = properties;
    }

    /**
     * Daily send budget of the upstream service, so an admin can size a campaign against
     * it before committing.
     */
    @GetMapping("/quota")
    public EmailServiceQuotaClient.Budget quota() {
        return fetchBudget();
    }

    /**
     * Dry run. Counts the audience and the opt-outs; sends nothing.
     */
    @PostMapping("/campaigns/preview")
    public MassEmailResponse preview(@Valid @RequestBody MassEmailRequest request) {
        guardFeatureEnabled();
        MassEmailUseCase.CampaignResult result = massEmailUseCase.preview(
                request.campaignId(), request.subject(), request.body(),
                request.variables(), request.priorityOrDefault());

        EmailServiceQuotaClient.Budget budget = fetchBudget();
        return toResponse(result, true, budget, previewMessage(result, budget));
    }

    /**
     * Live send. Refuses, rather than partially sending, when a guard trips.
     */
    @PostMapping("/campaigns")
    public MassEmailResponse send(@Valid @RequestBody MassEmailRequest request) {
        guardFeatureEnabled();

        // Count the audience before any mail leaves, both to enforce the size cap and to
        // warn about the upstream quota. This is one scan; the delivery scan below is the
        // second and unavoidable, because the send is what does the work. Running a third
        // scan (preview + send + this) doubled the per-user preference reads and meant the
        // preview and the send could report different audiences if a user changed their
        // preferences in between.
        MassEmailUseCase.CampaignResult dry = massEmailUseCase.preview(
                request.campaignId(), request.subject(), request.body(),
                request.variables(), request.priorityOrDefault());

        EmailServiceQuotaClient.Budget budget = fetchBudget();

        // Fail-safe: an absent properties bean must not silently disable the guard.
        int maxRecipients = maxRecipients();

        if (dry.truncated()) {
            return MassEmailResponse.blocked(
                    "audience exceeds the per-run limit of " + MassEmailUseCase.maxPerRun()
                            + " recipients; split the campaign",
                    budget.limit(), budget.used());
        }
        if (maxRecipients > 0 && dry.targeted() > maxRecipients) {
            return MassEmailResponse.blocked(
                    "audience of " + dry.targeted() + " exceeds the configured limit of "
                            + maxRecipients + "; raise notification.email-service.mass-mail-max-recipients"
                            + " or narrow the segment",
                    budget.limit(), budget.used());
        }
        if (budget.exhausted()) {
            return MassEmailResponse.blocked(
                    "the email service has exhausted its daily quota ("
                            + budget.used() + "/" + budget.limit() + "); it resets at midnight UTC",
                    budget.limit(), budget.used());
        }

        MassEmailUseCase.CampaignResult result = massEmailUseCase.send(
                request.campaignId(), request.subject(), request.body(),
                request.variables(), request.priorityOrDefault());

        log.info("Mass email campaign {}: targeted={} sent={} failed={} optedOut={}",
                result.campaignId(), result.targeted(), result.sent(),
                result.failed(), result.skippedOptedOut());

        String message = result.failed() == 0
                ? "Campaign sent to " + result.sent() + " recipient(s); "
                    + result.skippedOptedOut() + " opted out."
                : "Campaign sent to " + result.sent() + " recipient(s); " + result.failed()
                    + " failed and are recorded on their notification rows for retry.";

        return toResponse(result, false, budget, message);
    }

    /**
     * The configured recipient cap, or the hard backstop when the properties bean is
     * absent. Deliberately NOT 0: a 0 default meant the guard silently switched itself off
     * whenever configuration was missing, which is the opposite of fail-safe for the
     * highest-volume action in the product.
     */
    private int maxRecipients() {
        EmailServiceProperties config = properties.getIfAvailable();
        if (config == null) {
            return MassEmailUseCase.maxPerRun();
        }
        int configured = config.getMassMailMaxRecipients();
        return configured > 0 ? configured : MassEmailUseCase.maxPerRun();
    }

    private void guardFeatureEnabled() {
        EmailServiceProperties config = properties.getIfAvailable();
        if (config == null || !config.isMassMailEnabled()) {
            throw new ValidationException(
                    "Mass email is disabled. Set notification.email-service.mass-mail-enabled=true "
                            + "to enable it. See docs/EMAIL_SERVICE_INTEGRATION.md for the abuse "
                            + "and quota considerations before enabling.");
        }
    }

    /**
     * The upstream's real budget when it answers, otherwise the configured estimate.
     * Returning plain `unknown()` would leave the admin with no ceiling at all during an
     * upstream outage — exactly when they are most likely to send by mistake.
     */
    private EmailServiceQuotaClient.Budget fetchBudget() {
        EmailServiceQuotaClient client = quotaClient.getIfAvailable();
        if (client == null) {
            return EmailServiceQuotaClient.Budget.assumed(configuredDailyQuota());
        }
        EmailServiceQuotaClient.Budget budget = client.fetchDailyBudget();
        return budget.reachable() ? budget : EmailServiceQuotaClient.Budget.assumed(configuredDailyQuota());
    }

    private int configuredDailyQuota() {
        EmailServiceProperties config = properties.getIfAvailable();
        return config == null ? 0 : config.getDailyQuota();
    }

    private MassEmailResponse toResponse(MassEmailUseCase.CampaignResult result, boolean dryRun,
                                         EmailServiceQuotaClient.Budget budget, String message) {
        return new MassEmailResponse(
                result.campaignId(), dryRun,
                result.scanned(), result.targeted(), result.skippedOptedOut(),
                result.sent(), result.failed(), result.truncated(),
                null, null,
                budget.limit(), budget.used(),
                message);
    }

    private String previewMessage(MassEmailUseCase.CampaignResult result,
                                  EmailServiceQuotaClient.Budget budget) {
        StringBuilder message = new StringBuilder()
                .append("Would reach ").append(result.targeted()).append(" of ")
                .append(result.scanned()).append(" user(s); ")
                .append(result.skippedOptedOut()).append(" opted out of email.");

        if (budget.limit() != null && result.targeted() > budget.limit()) {
            message.append(" WARNING: this exceeds the email service's daily quota of ")
                    .append(budget.limit())
                    .append(", so most of it will be rejected. Raise the quota there first.");
        }
        if (!budget.reachable()) {
            message.append(" The email service did not report live usage, so the ")
                    .append(budget.limit() == null ? "quota is unknown" : "figure above is an estimate")
                    .append(". Verify capacity before sending.");
        }
        return message.toString();
    }

}