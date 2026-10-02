package com.zoner.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.auth.AuthDto.AuthResponse;
import com.zoner.auth.AuthDto.RegisterRequest;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.common.TestcontainersConfiguration;
import com.zoner.event.EventDto.AvailabilityRequest;
import com.zoner.event.EventDto.AvailabilityResponse;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.RecurrenceEditMode;
import com.zoner.event.EventDto.UpdateEventRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class RecurrenceIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private AuthResponse registerUser(String email, String name) {
        RegisterRequest req = new RegisterRequest(email, "password123", name, "America/New_York");
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
    @DisplayName("Standup every Monday: expansion, single-occurrence edit (THIS), and single-occurrence cancellation (THIS)")
    void standupEveryMondayLifecycleWithExceptions() {
        AuthResponse user = registerUser("standup@example.com", "Standup Lead");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        // 1. Create weekly standup every Monday at 10:00 AM EDT (14:00 UTC) for 30 minutes
        Instant firstStart = Instant.parse("2026-10-05T14:00:00Z");
        Instant firstEnd = Instant.parse("2026-10-05T14:30:00Z");

        CreateEventRequest createReq = new CreateEventRequest(
                calendarId,
                "Team Standup",
                "Weekly engineering sync",
                "Room A",
                "#3B82F6",
                false,
                firstStart,
                firstEnd,
                "America/New_York",
                "FREQ=WEEKLY;BYDAY=MO;INTERVAL=1",
                null
        );

        ResponseEntity<EventResponse> createResp = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(createReq, headers), EventResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        EventResponse created = createResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.recurring()).isTrue();
        assertThat(created.recurrenceRule()).isEqualTo("FREQ=WEEKLY;BYDAY=MO;INTERVAL=1");
        Long eventId = created.id();

        // 2. Query October 2026 (Oct 1 to Nov 1) -> Expect 4 occurrences (Oct 5, 12, 19, 26)
        ResponseEntity<List<EventResponse>> octResp = rest.exchange(
                "/api/events?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        assertThat(octResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<EventResponse> octEvents = octResp.getBody();
        assertThat(octEvents).isNotNull().hasSize(4);
        assertThat(octEvents.get(0).startAt()).isEqualTo(Instant.parse("2026-10-05T14:00:00Z"));
        assertThat(octEvents.get(1).startAt()).isEqualTo(Instant.parse("2026-10-12T14:00:00Z"));
        assertThat(octEvents.get(2).startAt()).isEqualTo(Instant.parse("2026-10-19T14:00:00Z"));
        assertThat(octEvents.get(3).startAt()).isEqualTo(Instant.parse("2026-10-26T14:00:00Z"));

        // 3. Edit single occurrence on Oct 12 (editMode = THIS): change title to "Sprint Demo" and push time by 1 hour (15:00 UTC)
        Instant oct12Orig = Instant.parse("2026-10-12T14:00:00Z");
        Instant oct12NewStart = Instant.parse("2026-10-12T15:00:00Z");
        Instant oct12NewEnd = Instant.parse("2026-10-12T15:30:00Z");

        UpdateEventRequest editThis = new UpdateEventRequest(
                calendarId,
                "Sprint Demo",
                "Bi-weekly sprint demo",
                "Main Auditorium",
                "#10B981",
                false,
                oct12NewStart,
                oct12NewEnd,
                "America/New_York",
                null,
                RecurrenceEditMode.THIS,
                oct12Orig,
                0L,
                null
        );

        ResponseEntity<EventResponse> editResp = rest.exchange(
                "/api/events/" + eventId, HttpMethod.PUT, new HttpEntity<>(editThis, headers), EventResponse.class);
        assertThat(editResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(editResp.getBody().title()).isEqualTo("Sprint Demo");
        assertThat(editResp.getBody().exception()).isTrue();

        // 4. Cancel single occurrence on Oct 19 (editMode = THIS)
        Instant oct19Orig = Instant.parse("2026-10-19T14:00:00Z");
        ResponseEntity<Void> cancelResp = rest.exchange(
                "/api/events/" + eventId + "?editMode=THIS&originalStart=" + oct19Orig,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 5. Re-query October: expect 3 occurrences:
        //    - Oct 5: "Team Standup" at 14:00
        //    - Oct 12: "Sprint Demo" at 15:00 (exception)
        //    - Oct 19: CANCELLED (not present)
        //    - Oct 26: "Team Standup" at 14:00
        ResponseEntity<List<EventResponse>> octAfterResp = rest.exchange(
                "/api/events?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        List<EventResponse> octAfter = octAfterResp.getBody();
        assertThat(octAfter).hasSize(3);

        assertThat(octAfter.get(0).title()).isEqualTo("Team Standup");
        assertThat(octAfter.get(0).startAt()).isEqualTo(Instant.parse("2026-10-05T14:00:00Z"));
        assertThat(octAfter.get(0).exception()).isFalse();

        assertThat(octAfter.get(1).title()).isEqualTo("Sprint Demo");
        assertThat(octAfter.get(1).startAt()).isEqualTo(Instant.parse("2026-10-12T15:00:00Z"));
        assertThat(octAfter.get(1).exception()).isTrue();

        assertThat(octAfter.get(2).title()).isEqualTo("Team Standup");
        assertThat(octAfter.get(2).startAt()).isEqualTo(Instant.parse("2026-10-26T14:00:00Z"));
        assertThat(octAfter.get(2).exception()).isFalse();
    }

    @Test
    @DisplayName("Recurrence accurately preserves local wall-clock time across DST boundary in America/New_York")
    void recurrencePreservesWallClockAcrossDstTransition() {
        AuthResponse user = registerUser("dst@example.com", "DST Tester");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        // Create weekly standup at 10:00 AM EDT (UTC 14:00) starting Oct 26, 2026
        // DST change occurs Nov 1, 2026: clocks turn backward 1 hour, EDT (UTC-4) becomes EST (UTC-5)
        Instant start = Instant.parse("2026-10-26T14:00:00Z");
        Instant end = Instant.parse("2026-10-26T14:30:00Z");

        CreateEventRequest req = new CreateEventRequest(
                calendarId, "DST Sync", null, null, null, false, start, end,
                "America/New_York", "FREQ=WEEKLY;BYDAY=MO", null);

        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(req, headers), EventResponse.class);

        // Query across DST transition: Oct 25 to Nov 15
        ResponseEntity<List<EventResponse>> resp = rest.exchange(
                "/api/events?from=2026-10-25T00:00:00Z&to=2026-11-15T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        List<EventResponse> events = resp.getBody();
        assertThat(events).hasSize(3);

        // 1. Oct 26 (EDT): 14:00 UTC = 10:00 AM local
        assertThat(events.get(0).startAt()).isEqualTo(Instant.parse("2026-10-26T14:00:00Z"));
        assertThat(events.get(0).startLocal().getHour()).isEqualTo(10);

        // 2. Nov 2 (EST): 15:00 UTC = 10:00 AM local! (1 hour UTC shift cleanly handled)
        assertThat(events.get(1).startAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        assertThat(events.get(1).startLocal().getHour()).isEqualTo(10);

        // 3. Nov 9 (EST): 15:00 UTC = 10:00 AM local!
        assertThat(events.get(2).startAt()).isEqualTo(Instant.parse("2026-11-09T15:00:00Z"));
        assertThat(events.get(2).startLocal().getHour()).isEqualTo(10);
    }

    @Test
    @DisplayName("Splitting series (THIS_AND_FOLLOWING) truncates original and starts new series")
    void splitSeriesThisAndFollowing() {
        AuthResponse user = registerUser("split@example.com", "Split User");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant start = Instant.parse("2026-10-05T14:00:00Z");
        Instant end = Instant.parse("2026-10-05T14:30:00Z");

        EventResponse master = rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Phase 1 Weekly", null, null, null, false, start, end,
                        "UTC", "FREQ=WEEKLY;BYDAY=MO", null), headers), EventResponse.class).getBody();
        assertThat(master).isNotNull();

        // Split series starting on Oct 19: Change title to "Phase 2 Weekly"
        Instant splitDate = Instant.parse("2026-10-19T14:00:00Z");
        UpdateEventRequest splitReq = new UpdateEventRequest(
                calendarId, "Phase 2 Weekly", null, null, null, false,
                splitDate, splitDate.plusSeconds(1800), "UTC", "FREQ=WEEKLY;BYDAY=MO",
                RecurrenceEditMode.THIS_AND_FOLLOWING, splitDate, 0L, null);

        ResponseEntity<EventResponse> splitResp = rest.exchange(
                "/api/events/" + master.id(), HttpMethod.PUT, new HttpEntity<>(splitReq, headers), EventResponse.class);
        assertThat(splitResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Query full month
        List<EventResponse> allEvents = rest.exchange(
                "/api/events?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<EventResponse>>() {}).getBody();
        assertThat(allEvents).hasSize(4);

        // First two occurrences belong to Phase 1
        assertThat(allEvents.get(0).title()).isEqualTo("Phase 1 Weekly");
        assertThat(allEvents.get(1).title()).isEqualTo("Phase 1 Weekly");

        // Last two occurrences belong to Phase 2
        assertThat(allEvents.get(2).title()).isEqualTo("Phase 2 Weekly");
        assertThat(allEvents.get(3).title()).isEqualTo("Phase 2 Weekly");
    }

    @Test
    @DisplayName("Availability checking is recurrence-aware and respects cancelled occurrences")
    void availabilityRecurrenceAwareness() {
        AuthResponse user = registerUser("avail.recur@example.com", "Avail Recur");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant start = Instant.parse("2026-10-05T14:00:00Z");
        Instant end = Instant.parse("2026-10-05T15:00:00Z");

        EventResponse series = rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Design Review", null, null, null, false, start, end,
                        "UTC", "FREQ=WEEKLY;BYDAY=MO", null), headers), EventResponse.class).getBody();
        assertThat(series).isNotNull();

        // Cancel occurrence on Oct 12
        Instant oct12 = Instant.parse("2026-10-12T14:00:00Z");
        rest.exchange("/api/events/" + series.id() + "?editMode=THIS&originalStart=" + oct12,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

        // Check availability on Oct 5 (14:15 - 14:45) -> CONFLICT (standup exists)
        AvailabilityRequest confReq = new AvailabilityRequest(
                Instant.parse("2026-10-05T14:15:00Z"),
                Instant.parse("2026-10-05T14:45:00Z"),
                List.of(calendarId),
                null
        );
        ResponseEntity<AvailabilityResponse> confResp = rest.exchange(
                "/api/events/availability", HttpMethod.POST, new HttpEntity<>(confReq, headers), AvailabilityResponse.class);
        assertThat(confResp.getBody().available()).isFalse();
        assertThat(confResp.getBody().conflicts()).hasSize(1);

        // Check availability on Oct 12 (14:15 - 14:45) -> AVAILABLE! (cancelled occurrence frees the slot)
        AvailabilityRequest freeReq = new AvailabilityRequest(
                Instant.parse("2026-10-12T14:15:00Z"),
                Instant.parse("2026-10-12T14:45:00Z"),
                List.of(calendarId),
                null
        );
        ResponseEntity<AvailabilityResponse> freeResp = rest.exchange(
                "/api/events/availability", HttpMethod.POST, new HttpEntity<>(freeReq, headers), AvailabilityResponse.class);
        assertThat(freeResp.getBody().available()).isTrue();
        assertThat(freeResp.getBody().conflicts()).isEmpty();
    }

    @Test
    @DisplayName("Invalid RRULE syntax is rejected with 422 BUSINESS_RULE_VIOLATION")
    void invalidRruleIsRejected() {
        AuthResponse user = registerUser("invalid.rrule@example.com", "Bad Rrule");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        CreateEventRequest badReq = new CreateEventRequest(
                calendarId, "Bad Rule", null, null, null, false,
                Instant.parse("2026-10-05T10:00:00Z"), Instant.parse("2026-10-05T11:00:00Z"),
                "UTC", "FREQ=INVALID_FREQ;NOT_A_RULE", null);

        ResponseEntity<Map> resp = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(badReq, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).containsEntry("code", "BUSINESS_RULE_VIOLATION");
    }
}
