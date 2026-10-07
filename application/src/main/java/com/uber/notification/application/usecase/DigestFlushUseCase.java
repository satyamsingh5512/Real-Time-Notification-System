package com.uber.notification.application.usecase;

import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.repository.NotificationRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flushes QUEUED_DIGEST rows as one grouped email per user (Slack/SuprSend digest
 * pattern). An empty digest never sends; a single-item digest reuses that item's
 * own subject line. Flushed rows are marked SENT with the digest recorded as the
 * rendered body prefix, and the caller (scheduler/admin) performs the actual
 * email send via the returned {@link Digest} payloads.
 */
public class DigestFlushUseCase {

    public record Digest(UUID userId, List<Notification> items, String subject, String body) {
    }

    private final NotificationRepository notificationRepository;
    private final int batchSize;

    public DigestFlushUseCase(NotificationRepository notificationRepository) {
        this(notificationRepository, 500);
    }

    public DigestFlushUseCase(NotificationRepository notificationRepository, int batchSize) {
        this.notificationRepository = notificationRepository;
        this.batchSize = batchSize;
    }

    /** Collects pending digests grouped by user without mutating state. */
    public List<Digest> collectPending() {
        List<Notification> queued = notificationRepository.findByStatus(NotificationStatus.QUEUED_DIGEST, batchSize);
        Map<UUID, List<Notification>> byUser = new LinkedHashMap<>();
        for (Notification n : queued) {
            byUser.computeIfAbsent(n.getUserId(), k -> new ArrayList<>()).add(n);
        }
        List<Digest> digests = new ArrayList<>();
        for (var entry : byUser.entrySet()) {
            digests.add(toDigest(entry.getKey(), entry.getValue()));
        }
        return digests;
    }

    /** Marks every item of a flushed digest SENT. Call after the email send succeeds. */
    public void markFlushed(Digest digest) {
        for (Notification item : digest.items()) {
            item.markSent();
            notificationRepository.save(item);
        }
    }

    private Digest toDigest(UUID userId, List<Notification> items) {
        String subject;
        StringBuilder body = new StringBuilder();
        if (items.size() == 1) {
            Notification only = items.get(0);
            subject = only.getRenderedSubject() != null ? only.getRenderedSubject()
                    : "You have 1 new update";
            body.append(only.getRenderedBody() != null ? only.getRenderedBody() : "");
        } else {
            subject = "You have " + items.size() + " new updates";
            for (Notification item : items) {
                body.append("• ");
                String line = item.getRenderedSubject() != null ? item.getRenderedSubject()
                        : item.getEventType().name();
                body.append(line);
                if (item.getRenderedBody() != null && !item.getRenderedBody().isBlank()) {
                    body.append(" — ").append(item.getRenderedBody());
                }
                body.append("\n");
            }
        }
        return new Digest(userId, List.copyOf(items), subject, body.toString());
    }
}
