package com.zoner.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zoner.auth.User;
import com.zoner.auth.UserRepository;
import com.zoner.calendar.AccessPolicy;
import com.zoner.calendar.Calendar;
import com.zoner.calendar.SharePermission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendeeUnitTest {

    private EventRepository eventRepository;
    private EventExceptionRepository eventExceptionRepository;
    private UserRepository userRepository;
    private AccessPolicy accessPolicy;
    private RecurrenceExpander recurrenceExpander;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        eventExceptionRepository = mock(EventExceptionRepository.class);
        userRepository = mock(UserRepository.class);
        accessPolicy = mock(AccessPolicy.class);
        recurrenceExpander = mock(RecurrenceExpander.class);

        eventService = new EventService(
                eventRepository,
                eventExceptionRepository,
                userRepository,
                accessPolicy,
                recurrenceExpander
        );
    }

    @Test
    @DisplayName("Should create event with attendees successfully")
    void createEventWithAttendees() {
        User creator = new User("owner@zoner.app", "hash", "Owner", "UTC");
        creator.setId(1L);
        Calendar calendar = new Calendar(creator, "Work", "desc", "#4F46E5", false);
        calendar.setId(10L);

        when(userRepository.getReferenceById(1L)).thenReturn(creator);
        when(accessPolicy.requireAccess(1L, 10L, SharePermission.EDIT))
                .thenReturn(new AccessPolicy.CalendarAccess(calendar, SharePermission.EDIT));

        Instant start = Instant.parse("2026-10-10T10:00:00Z");
        Instant end = Instant.parse("2026-10-10T11:00:00Z");

        List<EventDto.AttendeeDto> attendees = List.of(
                new EventDto.AttendeeDto(null, "colleague@zoner.app", "Colleague", AttendeeStatus.PENDING),
                new EventDto.AttendeeDto(null, "guest@company.com", "Guest", AttendeeStatus.ACCEPTED)
        );

        EventDto.CreateEventRequest request = new EventDto.CreateEventRequest(
                10L, "Sprint Review", "Discuss sprint", "Room A", "#4F46E5",
                false, start, end, "UTC", null, List.of(), attendees
        );

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setId(100L);
            return e;
        });

        EventDto.EventResponse response = eventService.createEvent(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.attendees()).hasSize(2);
        assertThat(response.attendees())
                .extracting(EventDto.AttendeeDto::email)
                .containsExactlyInAnyOrder("colleague@zoner.app", "guest@company.com");
    }

    @Test
    @DisplayName("Should update attendee RSVP status successfully")
    void rsvpEventSuccessfully() {
        User creator = new User("owner@zoner.app", "hash", "Owner", "UTC");
        creator.setId(1L);

        User attendeeUser = new User("colleague@zoner.app", "hash", "Alex", "UTC");
        attendeeUser.setId(2L);

        Calendar calendar = new Calendar(creator, "Work", "desc", "#4F46E5", false);
        calendar.setId(10L);

        Event event = new Event(calendar, "Sprint Review", "Desc", "Loc", "#4F46E5",
                false, Instant.parse("2026-10-10T10:00:00Z"), Instant.parse("2026-10-10T11:00:00Z"), "UTC", creator);
        event.setId(100L);
        event.addAttendee("colleague@zoner.app", "Alex", AttendeeStatus.PENDING);

        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(userRepository.findById(2L)).thenReturn(Optional.of(attendeeUser));
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventDto.EventResponse response = eventService.rsvpEvent(2L, 100L, AttendeeStatus.ACCEPTED);

        assertThat(response).isNotNull();
        assertThat(response.attendees()).hasSize(1);
        EventDto.AttendeeDto attendee = response.attendees().get(0);
        assertThat(attendee.email()).isEqualTo("colleague@zoner.app");
        assertThat(attendee.status()).isEqualTo(AttendeeStatus.ACCEPTED);
    }
}
