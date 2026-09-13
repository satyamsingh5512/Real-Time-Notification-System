package com.uber.notification.infrastructure.scheduler;

import com.uber.notification.domain.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

/**
 * Data-retention job: hard-deletes notifications older than the configured retention
 * window (default 90 days) in small batches so the {@code notifications} table stays
 * bounded under production write volumes. Soft-deleted rows are purged first; when none
 * remain the job falls back to purging the oldest rows regardless of flags (they are
 * past retention either way). A distributed lock keeps a single pod purging at a time.
 */
@Component
public class NotificationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);
    private static final String LOCK_KEY = "notification-retention-purge-lock";
    private static final int BATCH_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final LockRegistry lockRegistry;
    private final int retentionDays;
    private final boolean enabled;

    public NotificationRetentionJob(NotificationRepository notificationRepository,
                                    LockRegistry lockRegistry,
                                    @Value("${notification.retention.days:90}") int retentionDays,
                                    @Value("${notification.retention.enabled:true}") boolean enabled) {
        this.notificationRepository = notificationRepository;
        this.lockRegistry = lockRegistry;
        this.retentionDays = retentionDays;
        this.enabled = enabled;
    }

    // Daily at 02:00 UTC by default; overridable via notification.retention.cron.
    @Scheduled(cron = "${notification.retention.cron:0 0 2 * * *}")
    public void purgeExpired() {
        if (!enabled) {
            return;
        }
        Lock lock = lockRegistry.obtain(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int total = 0;
            while (true) {
                List<UUID> batch = notificationRepository.findIdsCreatedBefore(cutoff, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                notificationRepository.deleteByIds(batch);
                total += batch.size();
                log.info("Retention purge: deleted {} notifications older than {} (cutoff={})",
                        batch.size(), retentionDays + "d", cutoff);
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
            if (total > 0) {
                log.info("Retention purge complete: {} notification(s) older than {}d removed", total, retentionDays);
            }
        } finally {
            lock.unlock();
        }
    }
}
