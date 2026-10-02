package com.zoner.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.calendar.CalendarService;
import com.zoner.calendar.SharePermission;
import com.zoner.event.AvailabilityService;
import com.zoner.event.EventDto.AvailabilityRequest;
import com.zoner.event.EventDto.AvailabilityResponse;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.RecurrenceEditMode;
import com.zoner.event.EventService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class McpServiceTest {

    private CalendarService calendarService;
    private EventService eventService;
    private AvailabilityService availabilityService;
    private ObjectMapper objectMapper;
    private McpService mcpService;

    private final Long userId = 1L;
    private final String timeZone = "Asia/Kolkata";

    @BeforeEach
    void setUp() {
        calendarService = mock(CalendarService.class);
        eventService = mock(EventService.class);
        availabilityService = mock(AvailabilityService.class);
        objectMapper = new ObjectMapper();

        mcpService = new McpService(calendarService, eventService, availabilityService, objectMapper);
    }

    private EventResponse sampleResponse(Long id, String title, Instant start, Instant end) {
        return new EventResponse(
                id,
                1L,
                "Personal",
                "#4f46e5",
                title,
                "Sample description",
                "Room 101",
                "#4f46e5",
                false,
                start,
                end,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "UTC",
                null,
                null,
                false,
                false,
                1L,
                1L,
                Instant.now(),
                Instant.now(),
                List.of()
        );
    }

    @Test
    @DisplayName("initialize returns 2024-11-05 protocol version and tool capabilities")
    void testInitialize() {
        var res = mcpService.handleInitialize(Map.of());
        assertThat(res.protocolVersion()).isEqualTo("2024-11-05");
        assertThat(res.serverInfo().get("name")).isEqualTo("zoner-calendar");
        assertThat(res.capabilities()).containsKey("tools");
    }

    @Test
    @DisplayName("listTools registers all 9 required calendar tools")
    void testListTools() {
        var tools = mcpService.listTools();
        assertThat(tools).hasSize(9);
        List<String> names = tools.stream().map(McpProtocol.McpTool::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "list_calendars",
                "create_calendar",
                "list_events",
                "get_event",
                "create_event",
                "update_event",
                "delete_event",
                "search_events",
                "check_availability"
        );
    }

    @Test
    @DisplayName("list_calendars formats accessible calendars with ID and permissions")
    void testListCalendars() {
        CalendarResponse cal1 = new CalendarResponse(1L, "Personal", "Primary", "#4f46e5", true, SharePermission.OWNER, true, null, Instant.now());
        CalendarResponse cal2 = new CalendarResponse(2L, "Work", "Office", "#3b82f6", false, SharePermission.EDIT, true, null, Instant.now());
        when(calendarService.listCalendars(userId)).thenReturn(List.of(cal1, cal2));

        var result = mcpService.callTool(userId, timeZone, "list_calendars", Map.of());
        assertThat(result.isError()).isFalse();
        assertThat(result.content().getFirst().text()).contains("Personal", "[DEFAULT]", "Work", "EDIT");
    }

    @Test
    @DisplayName("create_event resolves default calendar when calendar parameter is omitted")
    void testCreateEventDefaultsToDefaultCalendar() {
        CalendarResponse defaultCal = new CalendarResponse(1L, "Personal", "", "#4f46e5", true, SharePermission.OWNER, true, null, Instant.now());
        when(calendarService.listCalendars(userId)).thenReturn(List.of(defaultCal));

        Instant start = Instant.parse("2026-10-05T10:00:00Z");
        Instant end = Instant.parse("2026-10-05T11:00:00Z");

        EventResponse created = sampleResponse(10L, "Coffee", start, end);
        when(eventService.createEvent(eq(userId), any(CreateEventRequest.class))).thenReturn(created);

        var result = mcpService.callTool(userId, timeZone, "create_event", Map.of(
                "title", "Coffee",
                "start", "2026-10-05T10:00:00Z",
                "end", "2026-10-05T11:00:00Z"
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content().getFirst().text()).contains("Successfully scheduled \"Coffee\"", "Personal");
    }

    @Test
    @DisplayName("create_event rejects creation on view-only shared calendar")
    void testCreateEventRejectsViewOnlyShare() {
        CalendarResponse viewOnlyCal = new CalendarResponse(5L, "Team Sync", "", "#10b981", false, SharePermission.VIEW, true, null, Instant.now());
        when(calendarService.listCalendars(userId)).thenReturn(List.of(viewOnlyCal));

        var result = mcpService.callTool(userId, timeZone, "create_event", Map.of(
                "title", "My Presentation",
                "calendar", "Team Sync",
                "start", "2026-10-05T10:00:00Z",
                "end", "2026-10-05T11:00:00Z"
        ));

        assertThat(result.isError()).isTrue();
        assertThat(result.content().getFirst().text()).contains("view-only access");
    }

    @Test
    @DisplayName("create_event gives helpful error listing available calendars if name not found")
    void testCreateEventUnknownCalendarListsAvailable() {
        CalendarResponse cal1 = new CalendarResponse(1L, "Work", "", "#4f46e5", true, SharePermission.OWNER, true, null, Instant.now());
        CalendarResponse cal2 = new CalendarResponse(2L, "Personal", "", "#3b82f6", false, SharePermission.OWNER, true, null, Instant.now());
        when(calendarService.listCalendars(userId)).thenReturn(List.of(cal1, cal2));

        var result = mcpService.callTool(userId, timeZone, "create_event", Map.of(
                "title", "Gym",
                "calendar", "NonExistentCal",
                "start", "2026-10-05T10:00:00Z",
                "end", "2026-10-05T11:00:00Z"
        ));

        assertThat(result.isError()).isTrue();
        assertThat(result.content().getFirst().text()).contains("NonExistentCal", "Available calendars: \"Work\", \"Personal\"");
    }

    @Test
    @DisplayName("check_availability reports FREE when no conflicts exist")
    void testCheckAvailabilityFree() {
        when(availabilityService.checkAvailability(eq(userId), any(AvailabilityRequest.class)))
                .thenReturn(new AvailabilityResponse(true, List.of()));

        var result = mcpService.callTool(userId, timeZone, "check_availability", Map.of(
                "start", "2026-10-05T14:00:00Z",
                "end", "2026-10-05T15:00:00Z"
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content().getFirst().text()).contains("FREE", "No conflicting events found");
    }

    @Test
    @DisplayName("check_availability reports BUSY and lists conflicting events when overlap exists")
    void testCheckAvailabilityBusy() {
        Instant start = Instant.parse("2026-10-05T14:00:00Z");
        Instant end = Instant.parse("2026-10-05T15:00:00Z");
        EventResponse conflict = sampleResponse(99L, "Sprint Review", start, end);

        when(availabilityService.checkAvailability(eq(userId), any(AvailabilityRequest.class)))
                .thenReturn(new AvailabilityResponse(false, List.of(conflict)));

        var result = mcpService.callTool(userId, timeZone, "check_availability", Map.of(
                "start", "2026-10-05T14:00:00Z",
                "end", "2026-10-05T15:00:00Z"
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content().getFirst().text()).contains("BUSY", "Sprint Review", "[ID: 99]");
    }

    @Test
    @DisplayName("delete_event calls service with specified editMode")
    void testDeleteEvent() {
        var result = mcpService.callTool(userId, timeZone, "delete_event", Map.of(
                "id", 100,
                "editMode", "THIS_AND_FOLLOWING",
                "occurrenceStart", "2026-10-05T09:00:00Z"
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content().getFirst().text()).contains("Successfully deleted event [ID: 100]");
        verify(eventService).deleteEvent(eq(userId), eq(100L), eq(RecurrenceEditMode.THIS_AND_FOLLOWING), any(Instant.class));
    }
}
