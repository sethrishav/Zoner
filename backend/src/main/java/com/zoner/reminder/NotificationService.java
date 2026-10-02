package com.zoner.reminder;

import com.zoner.common.error.NotFoundException;
import com.zoner.reminder.NotificationDto.NotificationResponse;
import com.zoner.reminder.NotificationDto.UnreadCountResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(Long userId, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, cappedLimit))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = notificationRepository.countUnreadByUserId(userId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found."));

        notification.markAsRead();
        notification = notificationRepository.save(notification);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId, clock.instant());
    }

    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found."));
        notificationRepository.delete(notification);
    }
}
