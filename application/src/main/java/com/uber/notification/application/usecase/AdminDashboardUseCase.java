package com.uber.notification.application.usecase;

import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Read-side analytics powering the admin dashboard: platform totals,
 * today's volume, global unread, and the read-rate percentage.
 */
public class AdminDashboardUseCase {

    public record DashboardStats(
            long totalNotifications,
            long totalUsers,
            long notificationsToday,
            long unreadNotifications,
            double readRatePercentage) {
    }

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public AdminDashboardUseCase(NotificationRepository notificationRepository,
                                 UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public DashboardStats stats() {
        long total = notificationRepository.countTotal();
        long today = notificationRepository.countCreatedSince(Instant.now().minus(1, ChronoUnit.DAYS));
        long unread = notificationRepository.countUnreadTotal();
        long read = notificationRepository.countReadTotal();
        double readRate = (read + unread) == 0 ? 0.0 : (100.0 * read) / (read + unread);
        return new DashboardStats(total, userRepository.count(), today, unread, readRate);
    }
}
