package com.zoner.reminder;

import com.zoner.auth.User;
import com.zoner.event.Event;
import com.zoner.event.ReminderChannel;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);
    private final NotificationRepository notificationRepository;

    public EmailNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public ReminderChannel getChannel() {
        return ReminderChannel.EMAIL;
    }

    @Override
    public void send(User recipient, Event event, Instant occurrenceStart, String title, String message) {
        log.info("[NOTIFICATION EVENT] [EMAIL CHANNEL] Dispatching email to recipient='{}' <{}> for eventId={}, eventTitle='{}', title='{}', message='{}'",
                recipient.getDisplayName(), recipient.getEmail(),
                event != null ? event.getId() : null,
                event != null ? event.getTitle() : "N/A",
                title, message);
        try {
            Notification notification = new Notification(recipient, event, occurrenceStart, title, message);
            Notification saved = notificationRepository.saveAndFlush(notification);
            log.info("[NOTIFICATION EVENT] In-app fallback notification ID={} saved for email recipient='{}' <{}>",
                    saved.getId(), recipient.getDisplayName(), recipient.getEmail());
        } catch (Exception e) {
            log.warn("[EMAIL CHANNEL] Could not persist in-app notification copy for email reminder: {}", e.getMessage());
        }
    }
}
