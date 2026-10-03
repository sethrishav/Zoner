package com.zoner.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zoner.auth.User;
import com.zoner.auth.UserRepository;
import com.zoner.calendar.Calendar;
import com.zoner.event.AttendeeStatus;
import com.zoner.event.Event;
import com.zoner.event.EventAttendee;
import com.zoner.event.EventAttendeeRepository;
import com.zoner.event.EventExceptionRepository;
import com.zoner.event.RecurrenceExpander;
import com.zoner.event.Reminder;
import com.zoner.event.ReminderChannel;
import com.zoner.event.ReminderRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderDispatcherUnitTest {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private ReminderDispatchRepository reminderDispatchRepository;

    @Mock
    private EventExceptionRepository eventExceptionRepository;

    @Mock
    private EventAttendeeRepository eventAttendeeRepository;

    @Mock
    private RecurrenceExpander recurrenceExpander;

    @Mock
    private NotificationChannelRegistry channelRegistry;

    @Mock
    private NotificationChannel notificationChannel;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private Clock clock;
    private ReminderDispatcher dispatcher;

    private final Instant now = Instant.parse("2026-10-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        dispatcher = new ReminderDispatcher(
                reminderRepository,
                reminderDispatchRepository,
                eventExceptionRepository,
                eventAttendeeRepository,
                recurrenceExpander,
                channelRegistry,
                userRepository,
                notificationRepository,
                clock
        );
    }

    private User createUser(Long id, String email, String name) {
        User u = new User(email, "hash", name, "UTC");
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (Exception ignored) {}
        return u;
    }

    private Event createEvent(Long id, User creator, Instant startAt) {
        Calendar cal = new Calendar(creator, "Work", null, "#000000", true);
        Event e = new Event(cal, "Team Meeting", "Discuss roadmap", "Room 1", "#3B82F6", false,
                startAt, startAt.plus(Duration.ofHours(1)), "UTC", creator);
        try {
            var field = Event.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(e, id);
        } catch (Exception ignored) {}
        return e;
    }

    @Test
    @DisplayName("Due reminder delivers to creator and attendees, then saves dispatch record")
    void dueReminderDispatchesSuccessfully() {
        User creator = createUser(1L, "creator@test.com", "Creator");
        User attendeeUser = createUser(2L, "attendee@test.com", "Attendee User");

        Instant occStart = now.plus(Duration.ofMinutes(5));
        Event event = createEvent(100L, creator, occStart);

        Reminder reminder = new Reminder(event, 5, ReminderChannel.IN_APP);
        try {
            var field = Reminder.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(reminder, 50L);
        } catch (Exception ignored) {}

        when(reminderRepository.findAllWithEventAndRecipients()).thenReturn(List.of(reminder));
        when(reminderDispatchRepository.existsByReminderIdAndOccurrenceStart(50L, occStart)).thenReturn(false);

        EventAttendee attendee = new EventAttendee(event, "attendee@test.com", "Attendee", AttendeeStatus.ACCEPTED);
        when(eventAttendeeRepository.findByEventId(100L)).thenReturn(List.of(attendee));
        when(userRepository.findByEmailIgnoreCase("attendee@test.com")).thenReturn(Optional.of(attendeeUser));
        when(channelRegistry.getChannel(ReminderChannel.IN_APP)).thenReturn(notificationChannel);

        int count = dispatcher.dispatchDueReminders(now);

        assertThat(count).isEqualTo(1);
        verify(notificationChannel).send(eq(creator), eq(event), eq(occStart), any(), any());
        verify(notificationChannel).send(eq(attendeeUser), eq(event), eq(occStart), any(), any());
        verify(reminderDispatchRepository).saveAndFlush(any(ReminderDispatch.class));
    }

    @Test
    @DisplayName("Strict idempotency: does not re-dispatch when dispatch record exists even if user deleted notification")
    void doesNotRedispatchWhenDispatchRecordExistsEvenIfNotificationDeleted() {
        User creator = createUser(1L, "creator@test.com", "Creator");
        Instant occStart = now.plus(Duration.ofMinutes(2));
        Event event = createEvent(200L, creator, occStart);

        Reminder reminder = new Reminder(event, 2, ReminderChannel.IN_APP);
        try {
            var field = Reminder.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(reminder, 70L);
        } catch (Exception ignored) {}

        when(reminderRepository.findAllWithEventAndRecipients()).thenReturn(List.of(reminder));
        // Reminder was already dispatched
        when(reminderDispatchRepository.existsByReminderIdAndOccurrenceStart(70L, occStart)).thenReturn(true);

        int count = dispatcher.dispatchDueReminders(now);

        // Strict idempotency: 0 dispatches, notification channel never called again
        assertThat(count).isEqualTo(0);
        verify(notificationChannel, never()).send(any(), any(), any(), any(), any());
        verify(reminderDispatchRepository, never()).saveAndFlush(any());
    }
}
