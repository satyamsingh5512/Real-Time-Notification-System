package com.uber.notification.infrastructure.scheduler;

import com.uber.notification.application.usecase.DeliverNotificationUseCase;
import com.uber.notification.application.usecase.NotificationHistoryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Polls for due scheduled/delayed notifications (task requirement: "Scheduling / Delayed
 * notifications") and hands them to the delivery use case. Runs on every API pod, but a
 * distributed lock ensures only one pod's poll executes at a time per interval, preventing
 * duplicate delivery attempts when the deployment is horizontally scaled.
 */
@Component
public class NotificationSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerJob.class);
    private static final String LOCK_KEY = "notification-scheduler-poll-lock";
    private static final int BATCH_SIZE = 100;

    private final NotificationHistoryUseCase historyUseCase;
    private final DeliverNotificationUseCase deliverNotificationUseCase;
    private final LockRegistry lockRegistry;

    public NotificationSchedulerJob(NotificationHistoryUseCase historyUseCase,
                                     DeliverNotificationUseCase deliverNotificationUseCase,
                                     LockRegistry lockRegistry) {
        this.historyUseCase = historyUseCase;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
        this.lockRegistry = lockRegistry;
    }

    @Scheduled(fixedDelayString = "${notification.scheduler.poll-interval-ms:10000}")
    public void pollDueNotifications() {
        Lock lock = lockRegistry.obtain(LOCK_KEY);
        if (!lock.tryLock()) {
            return; // another pod is already polling this cycle
        }
        try {
            List<com.uber.notification.domain.model.Notification> due = historyUseCase.findDueForDelivery(BATCH_SIZE);
            for (var notification : due) {
                try {
                    deliverNotificationUseCase.execute(notification);
                } catch (Exception e) {
                    log.error("Scheduled delivery failed for notification {}", notification.getId(), e);
                }
            }
            if (!due.isEmpty()) {
                log.info("Scheduler dispatched {} due notification(s)", due.size());
            }
        } finally {
            lock.unlock();
        }
    }
}
