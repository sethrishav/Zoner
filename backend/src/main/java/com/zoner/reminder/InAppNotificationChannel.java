package com.zoner.reminder;

import com.zoner.auth.User;
import com.zoner.event.Event;
import com.zoner.event.ReminderChannel;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationChannel.class);
    private final NotificationRepository notificationRepository;

    public InAppNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public ReminderChannel getChannel() {
        return ReminderChannel.IN_APP;
    }

    @Override
    public void send(User recipient, Event event, Instant occurrenceStart, String title, String message) {
        Notification notification = new Notification(recipient, event, occurrenceStart, title, message);
        Notification saved = notificationRepository.saveAndFlush(notification);
        log.info("[NOTIFICATION EVENT] In-app notification created successfully: notificationId={}, recipientId={}, recipientEmail='{}', eventId={}, eventTitle='{}', occStart='{}', title='{}'",
                saved.getId(), recipient.getId(), recipient.getEmail(),
                event != null ? event.getId() : null,
                event != null ? event.getTitle() : "N/A",
                occurrenceStart, title);
    }
}
