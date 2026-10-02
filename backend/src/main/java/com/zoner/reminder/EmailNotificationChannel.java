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

    @Override
    public ReminderChannel getChannel() {
        return ReminderChannel.EMAIL;
    }

    @Override
    public void send(User recipient, Event event, Instant occurrenceStart, String title, String message) {
        log.info("[EMAIL STUB] Sending email reminder to '{}' <{}>: {} - {}",
                recipient.getDisplayName(), recipient.getEmail(), title, message);
    }
}
