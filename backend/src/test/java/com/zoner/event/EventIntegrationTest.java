package com.zoner.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.zoner.auth.AuthDto.AuthResponse;
import com.zoner.auth.AuthDto.RegisterRequest;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.calendar.CalendarDto.CreateCalendarRequest;
import com.zoner.calendar.CalendarDto.ShareCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdatePreferenceRequest;
import com.zoner.calendar.SharePermission;
import com.zoner.common.TestcontainersConfiguration;
import com.zoner.event.EventDto.AvailabilityRequest;
import com.zoner.event.EventDto.AvailabilityResponse;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.ReminderDto;
import com.zoner.event.EventDto.UpdateEventRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class EventIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

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
    @DisplayName("Complete Event CRUD lifecycle with time zone conversion, reminders, and versioning")
    void eventCrudLifecycle() {
        AuthResponse user = registerUser("event.crud@example.com", "Event User");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant start = Instant.parse("2026-11-01T15:00:00Z"); // 10:00 AM New York (EDT: UTC-4 before Nov 1 transition, or EST: UTC-5)
        Instant end = Instant.parse("2026-11-01T16:00:00Z");

        // 1. Create Event with Reminder
        CreateEventRequest createReq = new CreateEventRequest(
                calendarId,
                "Architecture Review",
                "Review M3 design and OpenAPI specs",
                "Room 401",
                "#10B981",
                false,
                start,
                end,
                "America/New_York",
                List.of(new ReminderDto(null, 15, ReminderChannel.IN_APP))
        );

        ResponseEntity<EventResponse> createResp = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(createReq, headers), EventResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        EventResponse created = createResp.getBody();
        assertThat(created).isNotNull();
        assertThat(created.title()).isEqualTo("Architecture Review");
        assertThat(created.location()).isEqualTo("Room 401");
        assertThat(created.timeZone()).isEqualTo("America/New_York");
        assertThat(created.startAt()).isEqualTo(start);
        assertThat(created.endAt()).isEqualTo(end);
        assertThat(created.version()).isEqualTo(0L);
        assertThat(created.reminders()).hasSize(1);
        assertThat(created.reminders().get(0).minutesBefore()).isEqualTo(15);
        assertThat(created.reminders().get(0).channel()).isEqualTo(ReminderChannel.IN_APP);

        Long eventId = created.id();

        // 2. Retrieve Event
        ResponseEntity<EventResponse> getResp = rest.exchange(
                "/api/events/" + eventId, HttpMethod.GET, new HttpEntity<>(headers), EventResponse.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody()).isNotNull();
        assertThat(getResp.getBody().title()).isEqualTo("Architecture Review");

        // 3. Update Event
        UpdateEventRequest updateReq = new UpdateEventRequest(
                calendarId,
                "Architecture Review - Updated",
                "Expanded review with engineering lead",
                "Virtual Zoom",
                "#8B5CF6",
                false,
                start,
                end.plus(30, ChronoUnit.MINUTES),
                "America/New_York",
                0L, // current version
                List.of(
                        new ReminderDto(null, 10, ReminderChannel.IN_APP),
                        new ReminderDto(null, 60, ReminderChannel.EMAIL)
                )
        );

        ResponseEntity<EventResponse> updateResp = rest.exchange(
                "/api/events/" + eventId, HttpMethod.PUT, new HttpEntity<>(updateReq, headers), EventResponse.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        EventResponse updated = updateResp.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.title()).isEqualTo("Architecture Review - Updated");
        assertThat(updated.location()).isEqualTo("Virtual Zoom");
        assertThat(updated.reminders()).hasSize(2);

        // 4. Delete Event
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/events/" + eventId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 5. Verify 404 after deletion
        ResponseEntity<Map> postDeleteResp = rest.exchange(
                "/api/events/" + eventId, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(postDeleteResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Validation rejects endAt before or equal to startAt (422 BUSINESS_RULE_VIOLATION)")
    void rejectsEndBeforeOrEqualStart() {
        AuthResponse user = registerUser("validation@example.com", "Validator");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant now = Instant.parse("2026-10-10T12:00:00Z");

        // end equal to start
        CreateEventRequest equalReq = new CreateEventRequest(
                calendarId, "Zero duration", null, null, null, false,
                now, now, "UTC", null);
        ResponseEntity<Map> equalResp = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(equalReq, headers), Map.class);
        assertThat(equalResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(equalResp.getBody()).containsEntry("code", "BUSINESS_RULE_VIOLATION");

        // end before start
        CreateEventRequest beforeReq = new CreateEventRequest(
                calendarId, "Negative duration", null, null, null, false,
                now, now.minus(1, ChronoUnit.HOURS), "UTC", null);
        ResponseEntity<Map> beforeResp = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(beforeReq, headers), Map.class);
        assertThat(beforeResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(beforeResp.getBody()).containsEntry("code", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("Optimistic locking rejects stale version update with 409 STALE_VERSION")
    void optimisticLockingPreventsStaleUpdate() {
        AuthResponse user = registerUser("optimistic@example.com", "Optimistic User");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant start = Instant.parse("2026-10-15T09:00:00Z");
        Instant end = Instant.parse("2026-10-15T10:00:00Z");

        EventResponse created = rest.exchange(
                "/api/events", HttpMethod.POST,
                new HttpEntity<>(new CreateEventRequest(calendarId, "Sync", null, null, null, false, start, end, "UTC", null), headers),
                EventResponse.class).getBody();
        assertThat(created).isNotNull();
        assertThat(created.version()).isEqualTo(0L);

        // First update succeeds (moves version to 1)
        UpdateEventRequest update1 = new UpdateEventRequest(
                calendarId, "Sync v1", null, null, null, false, start, end, "UTC", 0L, null);
        ResponseEntity<EventResponse> resp1 = rest.exchange(
                "/api/events/" + created.id(), HttpMethod.PUT, new HttpEntity<>(update1, headers), EventResponse.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp1.getBody().version()).isEqualTo(1L);

        // Stale update (with old version 0L) fails with 409
        UpdateEventRequest staleUpdate = new UpdateEventRequest(
                calendarId, "Stale Sync", null, null, null, false, start, end, "UTC", 0L, null);
        ResponseEntity<Map> staleResp = rest.exchange(
                "/api/events/" + created.id(), HttpMethod.PUT, new HttpEntity<>(staleUpdate, headers), Map.class);
        assertThat(staleResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleResp.getBody()).containsEntry("code", "STALE_VERSION");
    }

    @Test
    @DisplayName("Range query correctly filters overlapping events and respects edge-touching intervals")
    void rangeQueryRespectsOverlapAndEdgeTouching() {
        AuthResponse user = registerUser("range@example.com", "Range User");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        // Event 1: 10:00 to 11:00
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Event 10-11", null, null, null, false,
                        Instant.parse("2026-10-20T10:00:00Z"), Instant.parse("2026-10-20T11:00:00Z"), "UTC", null), headers), EventResponse.class);

        // Event 2: 11:00 to 12:00 (exact edge touching with Event 1)
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Event 11-12", null, null, null, false,
                        Instant.parse("2026-10-20T11:00:00Z"), Instant.parse("2026-10-20T12:00:00Z"), "UTC", null), headers), EventResponse.class);

        // Event 3: 14:00 to 15:00
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Event 14-15", null, null, null, false,
                        Instant.parse("2026-10-20T14:00:00Z"), Instant.parse("2026-10-20T15:00:00Z"), "UTC", null), headers), EventResponse.class);

        // Query A: 10:30 to 11:30 (overlaps Event 1 and Event 2, but NOT Event 3)
        ResponseEntity<List<EventResponse>> queryA = rest.exchange(
                "/api/events?from=2026-10-20T10:30:00Z&to=2026-10-20T11:30:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        assertThat(queryA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queryA.getBody()).extracting(EventResponse::title)
                .containsExactly("Event 10-11", "Event 11-12");

        // Query B: 11:00 to 14:00
        // Edge touching: Event 1 ends at 11:00 -> NOT included (endAt > from is false)
        // Edge touching: Event 3 starts at 14:00 -> NOT included (startAt < to is false)
        // Only Event 2 (11-12) is included!
        ResponseEntity<List<EventResponse>> queryB = rest.exchange(
                "/api/events?from=2026-10-20T11:00:00Z&to=2026-10-20T14:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        assertThat(queryB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queryB.getBody()).extracting(EventResponse::title)
                .containsExactly("Event 11-12");
    }

    @Test
    @DisplayName("Range query respects user calendar visibility preferences")
    void rangeQueryRespectsCalendarVisibilityPreference() {
        AuthResponse user = registerUser("visibility@example.com", "Vis User");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long personalId = getDefaultCalendarId(user.accessToken());

        // Create secondary Work calendar
        CalendarResponse workCal = rest.exchange("/api/calendars", HttpMethod.POST,
                new HttpEntity<>(new CreateCalendarRequest("Work Projects", null, "#EF4444"), headers),
                CalendarResponse.class).getBody();
        assertThat(workCal).isNotNull();

        // Add event in Personal and Work
        Instant start = Instant.parse("2026-10-25T10:00:00Z");
        Instant end = Instant.parse("2026-10-25T11:00:00Z");

        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(personalId, "Personal Doctor", null, null, null, false, start, end, "UTC", null), headers), EventResponse.class);
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(workCal.id(), "Work Standup", null, null, null, false, start, end, "UTC", null), headers), EventResponse.class);

        // Initially both are visible
        List<EventResponse> initialList = rest.exchange(
                "/api/events?from=2026-10-25T00:00:00Z&to=2026-10-26T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<EventResponse>>() {}).getBody();
        assertThat(initialList).hasSize(2);

        // User hides Work calendar
        rest.exchange("/api/calendars/" + workCal.id() + "/preference", HttpMethod.PATCH,
                new HttpEntity<>(new UpdatePreferenceRequest(false, null), headers), CalendarResponse.class);

        // Unfiltered range query now only returns Personal Doctor
        List<EventResponse> afterHide = rest.exchange(
                "/api/events?from=2026-10-25T00:00:00Z&to=2026-10-26T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<EventResponse>>() {}).getBody();
        assertThat(afterHide).hasSize(1);
        assertThat(afterHide.get(0).title()).isEqualTo("Personal Doctor");

        // Explicitly requesting the hidden calendar still works
        List<EventResponse> explicitRequest = rest.exchange(
                "/api/events?from=2026-10-25T00:00:00Z&to=2026-10-26T00:00:00Z&calendarIds=" + workCal.id(),
                HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<EventResponse>>() {}).getBody();
        assertThat(explicitRequest).hasSize(1);
        assertThat(explicitRequest.get(0).title()).isEqualTo("Work Standup");
    }

    @Test
    @DisplayName("Search scopes across accessible calendars and matches title, description, and location")
    void searchScopesAcrossAccessibleCalendars() {
        AuthResponse user1 = registerUser("search1@example.com", "Search User 1");
        AuthResponse user2 = registerUser("search2@example.com", "Search User 2");
        HttpHeaders h1 = authHeaders(user1.accessToken());
        HttpHeaders h2 = authHeaders(user2.accessToken());

        Long cal1 = getDefaultCalendarId(user1.accessToken());
        Long cal2 = getDefaultCalendarId(user2.accessToken());

        Instant start = Instant.parse("2026-10-30T10:00:00Z");
        Instant end = Instant.parse("2026-10-30T11:00:00Z");

        // User 1 creates events
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(cal1, "Dentist Appointment", "Annual checkup", "Dental Clinic Suite 5", null, false, start, end, "UTC", null), h1), EventResponse.class);
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(cal1, "Team Lunch", "Italian bistro", "Downtown Bistro", null, false, start, end, "UTC", null), h1), EventResponse.class);

        // User 2 creates event with "Dentist" in title
        rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(cal2, "Dentist for User 2", "Secret", "Secret Clinic", null, false, start, end, "UTC", null), h2), EventResponse.class);

        // User 1 searches "dentist" -> only finds their own Dentist appointment
        ResponseEntity<List<EventResponse>> searchResp = rest.exchange(
                "/api/events/search?q=dentist", HttpMethod.GET, new HttpEntity<>(h1), new ParameterizedTypeReference<>() {});
        assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResp.getBody()).hasSize(1);
        assertThat(searchResp.getBody().get(0).title()).isEqualTo("Dentist Appointment");

        // User 1 searches location "Suite 5"
        ResponseEntity<List<EventResponse>> locSearch = rest.exchange(
                "/api/events/search?q=Suite 5", HttpMethod.GET, new HttpEntity<>(h1), new ParameterizedTypeReference<>() {});
        assertThat(locSearch.getBody()).hasSize(1);
        assertThat(locSearch.getBody().get(0).title()).isEqualTo("Dentist Appointment");
    }

    @Test
    @DisplayName("Anti-enumeration (404 on stranger's event) and view-only sharing permission enforcement (403 on edit)")
    void accessPolicyAndAntiEnumeration() {
        AuthResponse alice = registerUser("alice.event@example.com", "Alice");
        AuthResponse bob = registerUser("bob.event@example.com", "Bob");
        AuthResponse charlie = registerUser("charlie.event@example.com", "Charlie");

        HttpHeaders aliceHeaders = authHeaders(alice.accessToken());
        HttpHeaders bobHeaders = authHeaders(bob.accessToken());
        HttpHeaders charlieHeaders = authHeaders(charlie.accessToken());

        Long aliceCal = getDefaultCalendarId(alice.accessToken());

        Instant start = Instant.parse("2026-11-05T14:00:00Z");
        Instant end = Instant.parse("2026-11-05T15:00:00Z");

        EventResponse event = rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(aliceCal, "Alice Confidential Plan", "Design", "Headquarters", null, false, start, end, "UTC", null), aliceHeaders),
                EventResponse.class).getBody();
        assertThat(event).isNotNull();

        // 1. Bob (stranger) attempts GET -> 404 Not Found (no existence leak)
        ResponseEntity<Map> strangerGet = rest.exchange(
                "/api/events/" + event.id(), HttpMethod.GET, new HttpEntity<>(bobHeaders), Map.class);
        assertThat(strangerGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 2. Bob attempts PUT -> 404 Not Found
        UpdateEventRequest hackReq = new UpdateEventRequest(
                aliceCal, "Hacked", null, null, null, false, start, end, "UTC", 0L, null);
        ResponseEntity<Map> strangerPut = rest.exchange(
                "/api/events/" + event.id(), HttpMethod.PUT, new HttpEntity<>(hackReq, bobHeaders), Map.class);
        assertThat(strangerPut.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 3. Bob attempts DELETE -> 404 Not Found
        ResponseEntity<Map> strangerDel = rest.exchange(
                "/api/events/" + event.id(), HttpMethod.DELETE, new HttpEntity<>(bobHeaders), Map.class);
        assertThat(strangerDel.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 4. Alice shares calendar with Charlie with VIEW permission
        rest.exchange("/api/calendars/" + aliceCal + "/shares", HttpMethod.POST,
                new HttpEntity<>(new ShareCalendarRequest(charlie.user().email(), SharePermission.VIEW), aliceHeaders),
                com.zoner.calendar.CalendarDto.CalendarShareResponse.class);

        // Charlie can GET the event
        ResponseEntity<EventResponse> charlieGet = rest.exchange(
                "/api/events/" + event.id(), HttpMethod.GET, new HttpEntity<>(charlieHeaders), EventResponse.class);
        assertThat(charlieGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(charlieGet.getBody().title()).isEqualTo("Alice Confidential Plan");

        // Charlie CANNOT edit (403 Forbidden)
        ResponseEntity<Map> charliePut = rest.exchange(
                "/api/events/" + event.id(), HttpMethod.PUT, new HttpEntity<>(hackReq, charlieHeaders), Map.class);
        assertThat(charliePut.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Charlie CANNOT delete (403 Forbidden)
        ResponseEntity<Map> charlieDel = rest.exchange(
                "/api/events/" + event.id(), HttpMethod.DELETE, new HttpEntity<>(charlieHeaders), Map.class);
        assertThat(charlieDel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Charlie CANNOT create an event in Alice's calendar (403 Forbidden)
        ResponseEntity<Map> charlieCreate = rest.exchange(
                "/api/events", HttpMethod.POST, new HttpEntity<>(
                        new CreateEventRequest(aliceCal, "Charlie Injected", null, null, null, false, start, end, "UTC", null),
                        charlieHeaders), Map.class);
        assertThat(charlieCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Availability check identifies conflicts correctly and supports interval exclusions")
    void availabilityChecking() {
        AuthResponse user = registerUser("avail@example.com", "Availability User");
        HttpHeaders headers = authHeaders(user.accessToken());
        Long calendarId = getDefaultCalendarId(user.accessToken());

        Instant eventStart = Instant.parse("2026-11-10T14:00:00Z");
        Instant eventEnd = Instant.parse("2026-11-10T15:00:00Z");

        EventResponse created = rest.exchange("/api/events", HttpMethod.POST, new HttpEntity<>(
                new CreateEventRequest(calendarId, "Sprint Planning", null, null, null, false, eventStart, eventEnd, "UTC", null), headers),
                EventResponse.class).getBody();
        assertThat(created).isNotNull();

        // 1. Overlapping window (14:30 to 15:30) -> Conflict
        AvailabilityRequest conflictReq = new AvailabilityRequest(
                Instant.parse("2026-11-10T14:30:00Z"),
                Instant.parse("2026-11-10T15:30:00Z"),
                List.of(calendarId),
                null
        );
        ResponseEntity<AvailabilityResponse> conflictResp = rest.exchange(
                "/api/events/availability", HttpMethod.POST, new HttpEntity<>(conflictReq, headers), AvailabilityResponse.class);
        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(conflictResp.getBody().available()).isFalse();
        assertThat(conflictResp.getBody().conflicts()).hasSize(1);
        assertThat(conflictResp.getBody().conflicts().get(0).title()).isEqualTo("Sprint Planning");

        // 2. Exact edge touching window (15:00 to 16:00) -> NO Conflict
        AvailabilityRequest edgeReq = new AvailabilityRequest(
                Instant.parse("2026-11-10T15:00:00Z"),
                Instant.parse("2026-11-10T16:00:00Z"),
                List.of(calendarId),
                null
        );
        ResponseEntity<AvailabilityResponse> edgeResp = rest.exchange(
                "/api/events/availability", HttpMethod.POST, new HttpEntity<>(edgeReq, headers), AvailabilityResponse.class);
        assertThat(edgeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edgeResp.getBody().available()).isTrue();
        assertThat(edgeResp.getBody().conflicts()).isEmpty();

        // 3. Exclude event ID (e.g. when checking if moving the existing event to a new time is available)
        AvailabilityRequest excludeReq = new AvailabilityRequest(
                Instant.parse("2026-11-10T14:30:00Z"),
                Instant.parse("2026-11-10T15:30:00Z"),
                List.of(calendarId),
                created.id()
        );
        ResponseEntity<AvailabilityResponse> excludeResp = rest.exchange(
                "/api/events/availability", HttpMethod.POST, new HttpEntity<>(excludeReq, headers), AvailabilityResponse.class);
        assertThat(excludeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(excludeResp.getBody().available()).isTrue();
        assertThat(excludeResp.getBody().conflicts()).isEmpty();
    }
}
