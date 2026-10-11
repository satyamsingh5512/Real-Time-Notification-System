package com.uber.notification.infrastructure.scheduler;

import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.NotificationProviderRegistry;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.usecase.DigestFlushUseCase;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.infrastructure.metrics.NotificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Flushes pending digests as one grouped email per user (Slack/SuprSend pattern).
 * Runs hourly behind the same distributed lock used by the delivery scheduler so
 * only one pod flushes per window when horizontally scaled. Each digest is sent
 * through the normal {@link NotificationProvider} pipeline (same SES/Mock client,
 * recipient resolution, and error surface) rather than a bespoke mail path. An
 * empty digest is never sent; a failed digest stays QUEUED_DIGEST for the next
 * window instead of being dropped.
 */
@Component
public class DigestFlushJob {

    private static final Logger log = LoggerFactory.getLogger(DigestFlushJob.class);
    private static final String LOCK_KEY = "notification-digest-flush-lock";
    private static final String DIGEST_TEMPLATE_CODE = "DIGEST";

    private final DigestFlushUseCase digestFlushUseCase;
    private final NotificationProviderRegistry providerRegistry;
    private final ObjectProvider<RecipientResolverPort> recipientResolver;
    private final ObjectProvider<NotificationRepository> notificationRepository;
    private final LockRegistry lockRegistry;
    private final NotificationMetrics metrics;
    private final boolean enabled;

    public DigestFlushJob(DigestFlushUseCase digestFlushUseCase,
                          NotificationProviderRegistry providerRegistry,
                          ObjectProvider<RecipientResolverPort> recipientResolver,
                          ObjectProvider<NotificationRepository> notificationRepository,
                          LockRegistry lockRegistry,
                          ObjectProvider<NotificationMetrics> metrics,
                          @Value("${notification.digest.enabled:true}") boolean enabled) {
        this.digestFlushUseCase = digestFlushUseCase;
        this.providerRegistry = providerRegistry;
        this.recipientResolver = recipientResolver;
        this.notificationRepository = notificationRepository;
        this.lockRegistry = lockRegistry;
        this.metrics = metrics.getIfAvailable();
        this.enabled = enabled;
    }

    @Scheduled(cron = "${notification.digest.cron:0 15 * * * *}")
    public void flushPendingDigests() {
        if (!enabled) {
            return;
        }
        var lock = lockRegistry.obtain(LOCK_KEY);
        if (!lock.tryLock()) {
            return; // another pod owns this window
        }
        try {
            List<DigestFlushUseCase.Digest> digests = digestFlushUseCase.collectPending();
            NotificationMetrics m = metrics;
            if (m != null && digests.isEmpty()) {
                m.setPendingDigests(0);
            }
            if (digests.isEmpty()) {
                return;
            }
            NotificationProvider emailProvider = providerRegistry.resolve(NotificationChannel.EMAIL)
                    .orElseThrow(() -> new IllegalStateException("No EMAIL provider registered for digests"));
            RecipientResolverPort resolver = recipientResolver.getIfAvailable();

            int flushedItems = 0;
            int failedDigests = 0;
            for (DigestFlushUseCase.Digest digest : digests) {
                try {
                    ProviderRecipient recipient = resolver != null
                            ? resolver.resolve(digest.userId())
                            : new ProviderRecipient(null, null, null, null);
                    emailProvider.send(toDigestNotification(digest), recipient);
                    digestFlushUseCase.markFlushed(digest);
                    flushedItems += digest.items().size();
                } catch (Exception e) {
                    failedDigests++;
                    log.warn("Digest send failed for user {} (kept queued for next window): {}",
                            digest.userId(), e.getMessage());
                }
            }
            if (m != null) {
                m.incrementDigestFlushed(flushedItems);
                NotificationRepository repo = notificationRepository.getIfAvailable();
                if (repo != null) {
                    m.setPendingDigests((int) Math.min(Integer.MAX_VALUE,
                            repo.countByStatus(NotificationStatus.QUEUED_DIGEST)));
                }
            }
            log.info("Digest flush: {} digest(s) attempted, {} item(s) sent, {} failed",
                    digests.size(), flushedItems, failedDigests);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wraps a grouped digest as a synthetic EMAIL notification so providers receive
     * the same shape as a regular send (subject/body already rendered).
     */
    private Notification toDigestNotification(DigestFlushUseCase.Digest digest) {
        Notification n = new Notification(
                IdGenerator.newId(), digest.userId(), EventType.BROADCAST_ANNOUNCEMENT,
                NotificationChannel.EMAIL, DIGEST_TEMPLATE_CODE, Map.of("count", String.valueOf(digest.items().size())),
                1, null, Instant.now(), "digest:" + digest.userId() + ":" + Instant.now().toEpochMilli());
        n.markRendered(digest.subject(), digest.body());
        return n;
    }
}