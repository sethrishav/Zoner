package com.zoner.reminder;

import com.zoner.auth.User;
import com.zoner.auth.UserRepository;
import com.zoner.event.AttendeeStatus;
import com.zoner.event.Event;
import com.zoner.event.EventAttendee;
import com.zoner.event.EventAttendeeRepository;
import com.zoner.event.EventException;
import com.zoner.event.EventExceptionRepository;
import com.zoner.event.ExceptionType;
import com.zoner.event.RecurrenceExpander;
import com.zoner.event.Reminder;
import com.zoner.event.ReminderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);

    private final ReminderRepository reminderRepository;
    private final ReminderDispatchRepository reminderDispatchRepository;
    private final EventExceptionRepository eventExceptionRepository;
    private final EventAttendeeRepository eventAttendeeRepository;
    private final RecurrenceExpander recurrenceExpander;
    private final NotificationChannelRegistry channelRegistry;
    private final UserRepository userRepository;
    private final Clock clock;

    public ReminderDispatcher(
            ReminderRepository reminderRepository,
            ReminderDispatchRepository reminderDispatchRepository,
            EventExceptionRepository eventExceptionRepository,
            EventAttendeeRepository eventAttendeeRepository,
            RecurrenceExpander recurrenceExpander,
            NotificationChannelRegistry channelRegistry,
            UserRepository userRepository,
            Clock clock) {
        this.reminderRepository = reminderRepository;
        this.reminderDispatchRepository = reminderDispatchRepository;
        this.eventExceptionRepository = eventExceptionRepository;
        this.eventAttendeeRepository = eventAttendeeRepository;
        this.recurrenceExpander = recurrenceExpander;
        this.channelRegistry = channelRegistry;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Periodic scheduled evaluation. Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    @Transactional
    public void scheduledDispatch() {
        try {
            Instant now = clock.instant();
            int dispatched = dispatchDueReminders(now);
            if (dispatched > 0) {
                log.info("[REMINDER SCHEDULER] Dispatch cycle finished at {}: successfully dispatched {} reminder(s)", now, dispatched);
            } else {
                log.debug("[REMINDER SCHEDULER] Dispatch cycle finished at {}: no due reminders to dispatch", now);
            }
        } catch (Exception e) {
            log.error("[REMINDER SCHEDULER] Error executing scheduled reminder dispatch", e);
        }
    }

    /**
     * Evaluates all active reminders against the reference timestamp.
     * Catch-up safe: processes any reminder whose fire_at has passed and has not yet been dispatched.
     * Idempotent: enforces uniqueness on (reminder_id, occurrence_start).
     */
    @Transactional
    public int dispatchDueReminders(Instant now) {
        List<Reminder> reminders = reminderRepository.findAllWithEventAndRecipients();
        if (reminders.isEmpty()) {
            return 0;
        }

        log.info("[REMINDER EVAL] Checking {} active reminder(s) against reference time {}", reminders.size(), now);
        int dispatchCount = 0;

        for (Reminder reminder : reminders) {
            Event event = reminder.getEvent();

            if (event.getRecurrenceRule() == null || event.getRecurrenceRule().isBlank()) {
                // 1. Non-recurring event
                Instant occStart = event.getStartAt();
                Instant fireAt = occStart.minus(Duration.ofMinutes(reminder.getMinutesBefore()));

                // Catch-up window: fireAt <= now AND occStart occurred in last 24h or in future
                if (!fireAt.isAfter(now) && occStart.isAfter(now.minus(Duration.ofHours(24)))) {
                    log.info("[REMINDER TRIGGER] Non-recurring reminder ID={} for event ID={} ('{}') is DUE! occStart={}, fireAt={}, minutesBefore={}",
                            reminder.getId(), event.getId(), event.getTitle(), occStart, fireAt, reminder.getMinutesBefore());
                    if (dispatchIfDue(reminder, event, occStart, fireAt, null)) {
                        dispatchCount++;
                    }
                }
            } else {
                // 2. Recurring event series
                Instant searchFrom = now.minus(Duration.ofHours(24));
                Instant searchTo = now.plus(Duration.ofHours(24));

                List<Instant> occStarts = recurrenceExpander.expand(event, searchFrom, searchTo, 100);
                for (Instant occStart : occStarts) {
                    Optional<EventException> ex = eventExceptionRepository.findByEventIdAndOriginalStart(event.getId(), occStart);

                    if (ex.isPresent() && ex.get().getExceptionType() == ExceptionType.CANCELLED) {
                        // Skip cancelled occurrence
                        continue;
                    }

                    Instant effectiveStart = (ex.isPresent() && ex.get().getOverrideStartAt() != null)
                            ? ex.get().getOverrideStartAt()
                            : occStart;

                    Instant fireAt = effectiveStart.minus(Duration.ofMinutes(reminder.getMinutesBefore()));
                    if (!fireAt.isAfter(now) && effectiveStart.isAfter(now.minus(Duration.ofHours(24)))) {
                        log.info("[REMINDER TRIGGER] Recurring reminder ID={} for event ID={} ('{}') occurrence {} is DUE! fireAt={}, minutesBefore={}",
                                reminder.getId(), event.getId(), event.getTitle(), occStart, fireAt, reminder.getMinutesBefore());
                        if (dispatchIfDue(reminder, event, occStart, fireAt, ex.orElse(null))) {
                            dispatchCount++;
                        }
                    }
                }
            }
        }

        return dispatchCount;
    }

    private boolean dispatchIfDue(Reminder reminder, Event event, Instant occStart, Instant fireAt, EventException ex) {
        if (reminderDispatchRepository.existsByReminderIdAndOccurrenceStart(reminder.getId(), occStart)) {
            log.debug("[REMINDER DISPATCH] Reminder ID={} for event ID={} already dispatched for occurrence {}. Skipping duplicate.",
                    reminder.getId(), event.getId(), occStart);
            return false;
        }

        // 1. Resolve recipients safely without lazy proxy initialization issues
        Set<User> recipients = new HashSet<>();
        if (event.getCreatedBy() != null) {
            recipients.add(event.getCreatedBy());
        }
        if (event.getCalendar() != null && event.getCalendar().getOwner() != null) {
            recipients.add(event.getCalendar().getOwner());
        }
        try {
            List<EventAttendee> attendees = eventAttendeeRepository.findByEventId(event.getId());
            for (EventAttendee attendee : attendees) {
                if (attendee.getStatus() != AttendeeStatus.DECLINED && attendee.getEmail() != null && !attendee.getEmail().isBlank()) {
                    userRepository.findByEmailIgnoreCase(attendee.getEmail().trim()).ifPresent(recipients::add);
                }
            }
        } catch (Exception e) {
            log.warn("[REMINDER DISPATCH] Could not query attendees for event ID={}: {}", event.getId(), e.getMessage());
        }

        if (recipients.isEmpty()) {
            log.warn("[REMINDER DISPATCH] No valid recipients found for reminder ID={} on event ID={} ('{}'). Skipping delivery.",
                    reminder.getId(), event.getId(), event.getTitle());
            return false;
        }

        String eventTitle = (ex != null && ex.getOverrideTitle() != null)
                ? ex.getOverrideTitle()
                : event.getTitle();

        String title = "Reminder: " + eventTitle;
        String message = reminder.getMinutesBefore() == 0
                ? "'" + eventTitle + "' is starting now."
                : "'" + eventTitle + "' starts in " + reminder.getMinutesBefore() + " minutes.";

        NotificationChannel channel = channelRegistry.getChannel(reminder.getChannel());
        log.info("[REMINDER DISPATCH] Delivering reminder ID={} for event ID={} ('{}') via {} to {} recipient(s): {}",
                reminder.getId(), event.getId(), eventTitle, channel.getChannel(), recipients.size(),
                recipients.stream().map(User::getEmail).toList());

        int sentCount = 0;
        for (User recipient : recipients) {
            try {
                log.info("[REMINDER DISPATCH] Sending notification to user ID={} <{}> (channel={})",
                        recipient.getId(), recipient.getEmail(), channel.getChannel());
                channel.send(recipient, event, occStart, title, message);
                sentCount++;
            } catch (Exception e) {
                log.error("[REMINDER DISPATCH] Failed to send notification via {} to user ID={} for event ID={}: {}",
                        channel.getChannel(), recipient.getId(), event.getId(), e.getMessage(), e);
            }
        }

        // 2. Only persist dispatch record AFTER notifications are successfully processed
        if (sentCount > 0) {
            ReminderDispatch dispatch = new ReminderDispatch(reminder, occStart, fireAt, DispatchStatus.SENT);
            try {
                ReminderDispatch saved = reminderDispatchRepository.saveAndFlush(dispatch);
                Long dispatchId = (saved != null) ? saved.getId() : null;
                log.info("[REMINDER DISPATCH SUCCESS] Dispatch record ID={} created for reminder ID={} on event ID={} (sent to {}/{} recipients)",
                        dispatchId, reminder.getId(), event.getId(), sentCount, recipients.size());
            } catch (DataIntegrityViolationException e) {
                // Concurrent execution already dispatched this occurrence
                log.debug("[REMINDER DISPATCH] Reminder ID={} already dispatched concurrently for occurrence {}", reminder.getId(), occStart);
            }
            return true;
        } else {
            log.error("[REMINDER DISPATCH FAILURE] Failed to deliver reminder ID={} for event ID={} to any recipient. Dispatch record not saved.",
                    reminder.getId(), event.getId());
        }

        return false;
    }
}
