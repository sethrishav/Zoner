package com.zoner.reminder;

import com.zoner.auth.User;
import com.zoner.event.Event;
import com.zoner.event.ReminderChannel;
import java.time.Instant;

public interface NotificationChannel {

    ReminderChannel getChannel();

    void send(User recipient, Event event, Instant occurrenceStart, String title, String message);
}
