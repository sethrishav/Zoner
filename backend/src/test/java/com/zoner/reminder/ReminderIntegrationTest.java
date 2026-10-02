package com.zoner.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.auth.AuthDto.AuthResponse;
import com.zoner.auth.AuthDto.RegisterRequest;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.common.TestcontainersConfiguration;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.RecurrenceEditMode;
import com.zoner.event.EventDto.ReminderDto;
import com.zoner.event.ReminderChannel;
import com.zoner.reminder.NotificationDto.NotificationResponse;
import com.zoner.reminder.NotificationDto.UnreadCountResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReminderIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ReminderDispatcher reminderDispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ReminderDispatchRepository reminderDispatchRepository;

    private AuthResponse registerUser(String email, String name) {
        RegisterRequest req = new RegisterRequest(email, "password123", name, "UTC");
        return rest.postForEntity("/api/auth/register", req, AuthResponse.class).getBody();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Long getDefaultCalendarId(String token) {
        ResponseEntity<List<CalendarResponse>> response = rest.exchange(
                "/api/calendars",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().get(0).id();
    }

    @Test
    @DisplayName("Reminder due calculation, dispatch, and strict idempotency (no duplicate sends)")
    void reminderDueCalculationAndStrictIdempotency() {
        AuthResponse user = registerUser("reminder.test@example.com", "Reminder Tester");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant now = Instant.parse("2026-10-10T10:00:00Z");
        Instant eventStart = now.plus(Duration.ofMinutes(15)); // Starts at 10:15
        Instant eventEnd = eventStart.plus(Duration.ofMinutes(30));

        // Create event with 15-minute before IN_APP reminder
        CreateEventRequest req = new CreateEventRequest(
                calendarId,
                "Sprint Retro",
                "End of sprint review",
                "Conference Room 1",
                "#3B82F6",
                false,
                eventStart,
                eventEnd,
                "UTC",
                null,
                List.of(new ReminderDto(null, 15, ReminderChannel.IN_APP))
        );

        ResponseEntity<EventResponse> created = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(req, headers), EventResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 1. Dispatch at now (10:00:00) -> Event starts at 10:15, reminder is 15 min before -> due NOW!
        int dispatched = reminderDispatcher.dispatchDueReminders(now);
        assertThat(dispatched).isGreaterThanOrEqualTo(1);

        // 2. Query user notifications via REST API
        ResponseEntity<List<NotificationResponse>> notifResp = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        assertThat(notifResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<NotificationResponse> notifications = notifResp.getBody();
        assertThat(notifications).isNotNull().hasSize(1);
        assertThat(notifications.get(0).title()).isEqualTo("Reminder: Sprint Retro");
        assertThat(notifications.get(0).message()).contains("starts in 15 minutes");
        assertThat(notifications.get(0).read()).isFalse();

        // 3. IDEMPOTENCY TEST: Run dispatcher 3 more times consecutively at the same timestamp
        int secondRun = reminderDispatcher.dispatchDueReminders(now);
        int thirdRun = reminderDispatcher.dispatchDueReminders(now.plusSeconds(30));
        int fourthRun = reminderDispatcher.dispatchDueReminders(now.plusSeconds(60));

        // Zero additional dispatches
        assertThat(secondRun).isEqualTo(0);
        assertThat(thirdRun).isEqualTo(0);
        assertThat(fourthRun).isEqualTo(0);

        // Notification count remains EXACTLY 1 (no duplicate spam)
        ResponseEntity<List<NotificationResponse>> afterRuns = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        assertThat(afterRuns.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Recurring event reminder fires for active occurrences, but NOT for cancelled occurrences")
    void recurringEventRemindersRespectCancelledOccurrences() {
        AuthResponse user = registerUser("recur.reminder@example.com", "Recur Reminder");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        // Weekly standup at 10:00 UTC with 10-minute reminder
        Instant firstStart = Instant.parse("2026-10-05T10:00:00Z");
        Instant firstEnd = Instant.parse("2026-10-05T10:30:00Z");

        CreateEventRequest req = new CreateEventRequest(
                calendarId,
                "Daily Standup",
                "Daily team sync",
                "Virtual",
                null,
                false,
                firstStart,
                firstEnd,
                "UTC",
                "FREQ=WEEKLY;BYDAY=MO",
                List.of(new ReminderDto(null, 10, ReminderChannel.IN_APP))
        );

        EventResponse series = rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(req, headers), EventResponse.class).getBody();
        assertThat(series).isNotNull();

        // Cancel occurrence on Oct 12
        Instant oct12 = Instant.parse("2026-10-12T10:00:00Z");
        rest.exchange("/api/events/" + series.id() + "?editMode=THIS&originalStart=" + oct12,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

        // 1. Dispatch at Oct 5 09:50 (10 min before first occurrence) -> SHOULD dispatch!
        Instant oct5DispatchTime = Instant.parse("2026-10-05T09:50:00Z");
        reminderDispatcher.dispatchDueReminders(oct5DispatchTime);

        ResponseEntity<List<NotificationResponse>> notifs1 = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        assertThat(notifs1.getBody()).hasSize(1);
        assertThat(notifs1.getBody().get(0).title()).isEqualTo("Reminder: Daily Standup");
        assertThat(notifs1.getBody().get(0).occurrenceStart()).isEqualTo(firstStart);

        // 2. Dispatch at Oct 12 09:50 (10 min before cancelled occurrence) -> MUST NOT dispatch!
        Instant oct12DispatchTime = Instant.parse("2026-10-12T09:50:00Z");
        reminderDispatcher.dispatchDueReminders(oct12DispatchTime);

        // Still only 1 notification (from Oct 5)
        ResponseEntity<List<NotificationResponse>> notifs2 = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        assertThat(notifs2.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Notification API: unread count, mark single as read, mark all as read, and delete")
    void notificationApiLifecycle() {
        AuthResponse user = registerUser("notif.api@example.com", "Notif API");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        // Create 2 events with 0-minute reminders (fire at start)
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Event One", null, null, null, false,
                        now, now.plusSeconds(1800), "UTC", null,
                        List.of(new ReminderDto(null, 0, ReminderChannel.IN_APP))), headers), EventResponse.class);

        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Event Two", null, null, null, false,
                        now, now.plusSeconds(1800), "UTC", null,
                        List.of(new ReminderDto(null, 0, ReminderChannel.IN_APP))), headers), EventResponse.class);

        // Dispatch
        reminderDispatcher.dispatchDueReminders(now);

        // 1. Check unread count -> 2
        ResponseEntity<UnreadCountResponse> countResp = rest.exchange(
                "/api/notifications/unread-count", HttpMethod.GET, new HttpEntity<>(headers), UnreadCountResponse.class);
        assertThat(countResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countResp.getBody()).isNotNull();
        assertThat(countResp.getBody().unreadCount()).isEqualTo(2);

        // 2. List notifications
        List<NotificationResponse> list = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<NotificationResponse>>() {}).getBody();
        assertThat(list).hasSize(2);
        Long firstId = list.get(0).id();
        Long secondId = list.get(1).id();

        // 3. Mark single notification as read
        ResponseEntity<NotificationResponse> readResp = rest.exchange(
                "/api/notifications/" + firstId + "/read", HttpMethod.PATCH, new HttpEntity<>(headers), NotificationResponse.class);
        assertThat(readResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readResp.getBody().read()).isTrue();
        assertThat(readResp.getBody().readAt()).isNotNull();

        // Unread count is now 1
        assertThat(rest.exchange("/api/notifications/unread-count", HttpMethod.GET, new HttpEntity<>(headers), UnreadCountResponse.class).getBody().unreadCount())
                .isEqualTo(1);

        // 4. Mark all as read
        ResponseEntity<Void> markAllResp = rest.exchange(
                "/api/notifications/mark-all-read", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(markAllResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Unread count is now 0
        assertThat(rest.exchange("/api/notifications/unread-count", HttpMethod.GET, new HttpEntity<>(headers), UnreadCountResponse.class).getBody().unreadCount())
                .isEqualTo(0);

        // 5. Delete a notification
        ResponseEntity<Void> delResp = rest.exchange(
                "/api/notifications/" + secondId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Remaining list has size 1
        List<NotificationResponse> finalList = rest.exchange(
                "/api/notifications", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<NotificationResponse>>() {}).getBody();
        assertThat(finalList).hasSize(1);
    }
}
