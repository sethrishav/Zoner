package com.zoner.reminder;

import com.zoner.auth.User;
import com.zoner.event.Event;
import com.zoner.event.ReminderChannel;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel implements NotificationChannel {

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
        notificationRepository.save(notification);
    }
}
