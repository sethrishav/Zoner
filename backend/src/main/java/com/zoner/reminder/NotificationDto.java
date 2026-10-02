package com.zoner.reminder;

import java.time.Instant;

public final class NotificationDto {

    private NotificationDto() {}

    public record NotificationResponse(
            Long id,
            Long eventId,
            String eventTitle,
            Instant occurrenceStart,
            String title,
            String message,
            boolean read,
            Instant readAt,
            Instant createdAt
    ) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getId(),
                    n.getEvent() != null ? n.getEvent().getId() : null,
                    n.getEvent() != null ? n.getEvent().getTitle() : null,
                    n.getOccurrenceStart(),
                    n.getTitle(),
                    n.getMessage(),
                    n.isRead(),
                    n.getReadAt(),
                    n.getCreatedAt()
            );
        }
    }

    public record UnreadCountResponse(
            long unreadCount
    ) {}
}
