package com.zoner.reminder;

import com.zoner.auth.User;
import com.zoner.event.Event;
import com.zoner.event.ReminderChannel;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    @Override
    public ReminderChannel getChannel() {
        return ReminderChannel.SMS;
    }

    @Override
    public void send(User recipient, Event event, Instant occurrenceStart, String title, String message) {
        log.info("[SMS STUB] Sending SMS reminder to '{}': {} - {}",
                recipient.getDisplayName(), title, message);
    }
}
