package com.zoner.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.calendar.CalendarDto.CreateCalendarRequest;
import com.zoner.calendar.CalendarService;
import com.zoner.calendar.SharePermission;
import com.zoner.common.error.BusinessRuleException;
import com.zoner.common.error.ForbiddenException;
import com.zoner.common.error.NotFoundException;
import com.zoner.event.AvailabilityService;
import com.zoner.event.EventDto.AvailabilityRequest;
import com.zoner.event.EventDto.AvailabilityResponse;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.UpdateEventRequest;
import com.zoner.event.EventService;
import com.zoner.event.EventDto.RecurrenceEditMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class McpService {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);

    private final CalendarService calendarService;
    private final EventService eventService;
    private final AvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    public McpService(
            CalendarService calendarService,
            EventService eventService,
            AvailabilityService availabilityService,
            ObjectMapper objectMapper) {
        this.calendarService = calendarService;
        this.eventService = eventService;
        this.availabilityService = availabilityService;
        this.objectMapper = objectMapper;
    }

    public McpProtocol.McpInitializeResult handleInitialize(Map<String, Object> params) {
        Map<String, Object> capabilities = Map.of("tools", Map.of());
        Map<String, Object> serverInfo = Map.of(
                "name", "zoner-calendar",
                "version", "1.0.0"
        );
        return new McpProtocol.McpInitializeResult(McpProtocol.PROTOCOL_VERSION, capabilities, serverInfo);
    }

    public List<McpProtocol.McpTool> listTools() {
        return List.of(
                createTool(
                        "list_calendars",
                        "Lists all calendars accessible to the user (owned and shared), including their IDs, names, colors, and permissions.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of()
                        )
                ),
                createTool(
                        "create_calendar",
                        "Creates a new calendar for the authenticated user.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of("type", "string", "description", "Calendar name"),
                                        "color", Map.of("type", "string", "description", "Hex color code (e.g. '#4f46e5')"),
                                        "description", Map.of("type", "string", "description", "Optional calendar description")
                                ),
                                "required", List.of("name")
                        )
                ),
                createTool(
                        "list_events",
                        "Lists events within a time range across accessible calendars. Automatically expands recurring events into actual occurrences.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of("type", "string", "description", "Start of the time range in ISO-8601 format (e.g. '2026-10-01T00:00:00Z')"),
                                        "end", Map.of("type", "string", "description", "End of the time range in ISO-8601 format (e.g. '2026-10-07T23:59:59Z')"),
                                        "calendarIds", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "integer"),
                                                "description", "Optional list of calendar IDs to filter by. Defaults to all active calendars."
                                        )
                                ),
                                "required", List.of("start", "end")
                        )
                ),
                createTool(
                        "get_event",
                        "Retrieves full details of a specific event by its ID. Respects calendar permissions.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "description", "The ID of the event to retrieve")
                                ),
                                "required", List.of("id")
                        )
                ),
                createTool(
                        "create_event",
                        "Schedules a new event on a calendar. AI-friendly: accepts calendar by name (e.g. 'Work') or ID. If omitted, uses your primary default calendar.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "title", Map.of("type", "string", "description", "Event title or meeting summary"),
                                        "start", Map.of("type", "string", "description", "Start time in ISO-8601 format (e.g. '2026-10-05T15:00:00Z')"),
                                        "end", Map.of("type", "string", "description", "End time in ISO-8601 format (e.g. '2026-10-05T16:00:00Z')"),
                                        "calendar", Map.of("type", "string", "description", "Calendar name (e.g. 'Work', 'Personal') or numeric calendar ID. Defaults to default calendar."),
                                        "allDay", Map.of("type", "boolean", "description", "Whether the event is an all-day event"),
                                        "location", Map.of("type", "string", "description", "Physical location, conference room, or video link"),
                                        "description", Map.of("type", "string", "description", "Detailed event notes, agenda, or description"),
                                        "recurrenceRule", Map.of("type", "string", "description", "iCalendar RRULE string for recurring events (e.g. 'FREQ=WEEKLY;BYDAY=MO')")
                                ),
                                "required", List.of("title", "start", "end")
                        )
                ),
                createTool(
                        "update_event",
                        "Updates an existing event. For recurring events, specify editMode: 'THIS' (this occurrence only), 'THIS_AND_FOLLOWING' (this and future occurrences), or 'ALL' (all occurrences).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "description", "Event ID to update"),
                                        "title", Map.of("type", "string", "description", "Updated event title"),
                                        "start", Map.of("type", "string", "description", "Updated start time in ISO-8601 format"),
                                        "end", Map.of("type", "string", "description", "Updated end time in ISO-8601 format"),
                                        "location", Map.of("type", "string", "description", "Updated location"),
                                        "description", Map.of("type", "string", "description", "Updated description"),
                                        "editMode", Map.of(
                                                "type", "string",
                                                "enum", List.of("THIS", "THIS_AND_FOLLOWING", "ALL"),
                                                "description", "Scope of recurring update: 'THIS', 'THIS_AND_FOLLOWING', or 'ALL' (default)"
                                        ),
                                        "occurrenceStart", Map.of("type", "string", "description", "Original start time of occurrence when editMode is THIS or THIS_AND_FOLLOWING")
                                ),
                                "required", List.of("id")
                        )
                ),
                createTool(
                        "delete_event",
                        "DESTRUCTIVE: Deletes an event or occurrence from the calendar. For recurring events, specify editMode: 'THIS', 'THIS_AND_FOLLOWING', or 'ALL'.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer", "description", "Event ID to delete"),
                                        "editMode", Map.of(
                                                "type", "string",
                                                "enum", List.of("THIS", "THIS_AND_FOLLOWING", "ALL"),
                                                "description", "Deletion scope (default 'ALL')"
                                        ),
                                        "occurrenceStart", Map.of("type", "string", "description", "Original start of occurrence when deleting a single occurrence")
                                ),
                                "required", List.of("id")
                        )
                ),
                createTool(
                        "search_events",
                        "Searches events across titles, descriptions, and locations in accessible calendars.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "Search query or keyword"),
                                        "limit", Map.of("type", "integer", "description", "Maximum results to return (default 20)")
                                ),
                                "required", List.of("query")
                        )
                ),
                createTool(
                        "check_availability",
                        "Checks if a proposed time window is free or busy across calendars. Returns all conflicting events with their titles, times, and calendars.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start", Map.of("type", "string", "description", "Proposed start time in ISO-8601 format"),
                                        "end", Map.of("type", "string", "description", "Proposed end time in ISO-8601 format"),
                                        "calendarIds", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "integer"),
                                                "description", "Specific calendar IDs to check. Defaults to all accessible calendars."
                                        ),
                                        "excludeEventId", Map.of("type", "integer", "description", "Event ID to exclude from conflict check")
                                ),
                                "required", List.of("start", "end")
                        )
                )
        );
    }

    public McpProtocol.McpToolResult callTool(
            Long userId,
            String userTimeZone,
            String toolName,
            Map<String, Object> arguments) {
        try {
            Map<String, Object> args = arguments != null ? arguments : Collections.emptyMap();
            return switch (toolName) {
                case "list_calendars" -> handleListCalendars(userId);
                case "create_calendar" -> handleCreateCalendar(userId, args);
                case "list_events" -> handleListEvents(userId, args, userTimeZone);
                case "get_event" -> handleGetEvent(userId, args);
                case "create_event" -> handleCreateEvent(userId, args, userTimeZone);
                case "update_event" -> handleUpdateEvent(userId, args, userTimeZone);
                case "delete_event" -> handleDeleteEvent(userId, args);
                case "search_events" -> handleSearchEvents(userId, args);
                case "check_availability" -> handleCheckAvailability(userId, args, userTimeZone);
                default -> McpProtocol.McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (NotFoundException e) {
            return McpProtocol.McpToolResult.error("Resource not found: " + e.getMessage());
        } catch (ForbiddenException e) {
            return McpProtocol.McpToolResult.error("Permission denied: " + e.getMessage());
        } catch (BusinessRuleException | IllegalArgumentException e) {
            return McpProtocol.McpToolResult.error("Invalid request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return McpProtocol.McpToolResult.error("Error executing tool '" + toolName + "': " + e.getMessage());
        }
    }

    private McpProtocol.McpToolResult handleListCalendars(Long userId) {
        List<CalendarResponse> calendars = calendarService.listCalendars(userId);
        if (calendars.isEmpty()) {
            return McpProtocol.McpToolResult.success("No accessible calendars found.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Accessible Calendars (%d):\n", calendars.size()));
        for (CalendarResponse cal : calendars) {
            String defaultBadge = cal.isDefault() ? " [DEFAULT]" : "";
            boolean isOwner = cal.permission() == SharePermission.OWNER;
            String ownerBadge = isOwner ? " (Owner)" : " (Shared: " + cal.permission() + ")";
            sb.append(String.format("- [ID: %d] \"%s\"%s%s | Color: %s\n",
                    cal.id(), cal.name(), defaultBadge, ownerBadge, cal.color()));
        }
        return McpProtocol.McpToolResult.success(sb.toString().trim());
    }

    private McpProtocol.McpToolResult handleCreateCalendar(Long userId, Map<String, Object> args) {
        String name = getString(args, "name");
        String color = getString(args, "color");
        String description = getString(args, "description");

        CreateCalendarRequest req = new CreateCalendarRequest(name, description, color);
        CalendarResponse created = calendarService.createCalendar(userId, req);

        return McpProtocol.McpToolResult.success(
                String.format("Successfully created calendar \"%s\" [ID: %d].",
                        created.name(), created.id()));
    }

    private McpProtocol.McpToolResult handleListEvents(Long userId, Map<String, Object> args, String userTimeZone) {
        Instant start = parseInstant(getString(args, "start"), userTimeZone);
        Instant end = parseInstant(getString(args, "end"), userTimeZone);
        List<Long> calendarIds = getLongList(args, "calendarIds");

        List<EventResponse> events = eventService.listEventsInRange(userId, start, end, calendarIds);
        if (events.isEmpty()) {
            return McpProtocol.McpToolResult.success(
                    String.format("No events scheduled between %s and %s.", start, end));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d event(s) between %s and %s:\n", events.size(), start, end));
        for (int i = 0; i < events.size(); i++) {
            EventResponse ev = events.get(i);
            String recurBadge = ev.recurrenceRule() != null ? " [Recurring]" : "";
            sb.append(String.format("%d. \"%s\" [ID: %d]%s\n", i + 1, ev.title(), ev.id(), recurBadge));
            sb.append(String.format("   Time: %s to %s\n", ev.startAt(), ev.endAt()));
            if (ev.location() != null && !ev.location().isBlank()) {
                sb.append(String.format("   Location: %s\n", ev.location()));
            }
            if (ev.description() != null && !ev.description().isBlank()) {
                sb.append(String.format("   Description: %s\n", ev.description()));
            }
        }
        return McpProtocol.McpToolResult.success(sb.toString().trim());
    }

    private McpProtocol.McpToolResult handleGetEvent(Long userId, Map<String, Object> args) {
        Long id = getLong(args, "id");
        EventResponse ev = eventService.getEvent(userId, id);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Event [ID: %d]: \"%s\"\n", ev.id(), ev.title()));
        sb.append(String.format("Time: %s to %s (All day: %s)\n", ev.startAt(), ev.endAt(), ev.allDay()));
        sb.append(String.format("Calendar ID: %d | TimeZone: %s\n", ev.calendarId(), ev.timeZone()));
        if (ev.location() != null) sb.append(String.format("Location: %s\n", ev.location()));
        if (ev.description() != null) sb.append(String.format("Description: %s\n", ev.description()));
        if (ev.recurrenceRule() != null) sb.append(String.format("Recurrence: %s\n", ev.recurrenceRule()));

        return McpProtocol.McpToolResult.success(sb.toString().trim());
    }

    private McpProtocol.McpToolResult handleCreateEvent(Long userId, Map<String, Object> args, String userTimeZone) {
        String title = getString(args, "title");
        Instant start = parseInstant(getString(args, "start"), userTimeZone);
        Instant end = parseInstant(getString(args, "end"), userTimeZone);
        boolean allDay = Boolean.TRUE.equals(args.get("allDay"));
        String location = getString(args, "location");
        String description = getString(args, "description");
        String recurrenceRule = getString(args, "recurrenceRule");

        // Resolve calendar: name or ID, or default
        List<CalendarResponse> userCalendars = calendarService.listCalendars(userId);
        CalendarResponse targetCalendar = resolveTargetCalendar(userCalendars, args.get("calendar"));

        if (targetCalendar.permission() == SharePermission.VIEW) {
            return McpProtocol.McpToolResult.error(
                    String.format("Cannot create event on calendar \"%s\". You only have view-only access.", targetCalendar.name()));
        }

        CreateEventRequest req = new CreateEventRequest(
                targetCalendar.id(),
                title,
                description,
                location,
                targetCalendar.color(),
                allDay,
                start,
                end,
                userTimeZone != null ? userTimeZone : "UTC",
                recurrenceRule,
                List.of()
        );

        EventResponse created = eventService.createEvent(userId, req);

        return McpProtocol.McpToolResult.success(
                String.format("Successfully scheduled \"%s\" [ID: %d] on calendar \"%s\" from %s to %s.",
                        created.title(), created.id(), targetCalendar.name(), created.startAt(), created.endAt()));
    }

    private McpProtocol.McpToolResult handleUpdateEvent(Long userId, Map<String, Object> args, String userTimeZone) {
        Long id = getLong(args, "id");
        EventResponse existing = eventService.getEvent(userId, id);

        String title = getString(args, "title");
        if (title == null) title = existing.title();

        Instant start = args.containsKey("start") ? parseInstant(getString(args, "start"), userTimeZone) : existing.startAt();
        Instant end = args.containsKey("end") ? parseInstant(getString(args, "end"), userTimeZone) : existing.endAt();

        String location = args.containsKey("location") ? getString(args, "location") : existing.location();
        String description = args.containsKey("description") ? getString(args, "description") : existing.description();

        RecurrenceEditMode editMode = RecurrenceEditMode.ALL;
        if (args.containsKey("editMode")) {
            editMode = RecurrenceEditMode.valueOf(getString(args, "editMode").toUpperCase());
        }

        Instant occurrenceStart = null;
        if (args.containsKey("occurrenceStart")) {
            occurrenceStart = parseInstant(getString(args, "occurrenceStart"), userTimeZone);
        }

        UpdateEventRequest req = new UpdateEventRequest(
                existing.calendarId(),
                title,
                description,
                location,
                existing.color(),
                existing.allDay(),
                start,
                end,
                existing.timeZone(),
                existing.recurrenceRule(),
                editMode,
                occurrenceStart,
                existing.version(),
                List.of()
        );

        EventResponse updated = eventService.updateEvent(userId, id, req);

        return McpProtocol.McpToolResult.success(
                String.format("Successfully updated event \"%s\" [ID: %d] (EditMode: %s).",
                        updated.title(), updated.id(), editMode));
    }

    private McpProtocol.McpToolResult handleDeleteEvent(Long userId, Map<String, Object> args) {
        Long id = getLong(args, "id");

        RecurrenceEditMode editMode = RecurrenceEditMode.ALL;
        if (args.containsKey("editMode")) {
            editMode = RecurrenceEditMode.valueOf(getString(args, "editMode").toUpperCase());
        }

        Instant occurrenceStart = null;
        if (args.containsKey("occurrenceStart")) {
            occurrenceStart = parseInstant(getString(args, "occurrenceStart"), "UTC");
        }

        eventService.deleteEvent(userId, id, editMode, occurrenceStart);

        return McpProtocol.McpToolResult.success(
                String.format("Successfully deleted event [ID: %d] (EditMode: %s).", id, editMode));
    }

    private McpProtocol.McpToolResult handleSearchEvents(Long userId, Map<String, Object> args) {
        String query = getString(args, "query");
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;

        List<EventResponse> results = eventService.searchEvents(userId, query, null, null, limit);
        if (results.isEmpty()) {
            return McpProtocol.McpToolResult.success("No events matched query: \"" + query + "\"");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d event(s) matching \"%s\":\n", results.size(), query));
        for (int i = 0; i < results.size(); i++) {
            EventResponse ev = results.get(i);
            sb.append(String.format("%d. \"%s\" [ID: %d] on %s\n", i + 1, ev.title(), ev.id(), ev.startAt()));
            if (ev.location() != null) sb.append(String.format("   Location: %s\n", ev.location()));
        }
        return McpProtocol.McpToolResult.success(sb.toString().trim());
    }

    private McpProtocol.McpToolResult handleCheckAvailability(Long userId, Map<String, Object> args, String userTimeZone) {
        Instant start = parseInstant(getString(args, "start"), userTimeZone);
        Instant end = parseInstant(getString(args, "end"), userTimeZone);
        List<Long> calendarIds = getLongList(args, "calendarIds");
        Long excludeEventId = args.containsKey("excludeEventId") ? getLong(args, "excludeEventId") : null;

        AvailabilityRequest req = new AvailabilityRequest(start, end, calendarIds, excludeEventId, null);
        AvailabilityResponse res = availabilityService.checkAvailability(userId, req);

        if (res.available()) {
            return McpProtocol.McpToolResult.success(
                    String.format("Time window %s to %s is FREE. No conflicting events found.", start, end));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Time window %s to %s is BUSY. Found %d conflicting event(s):\n",
                start, end, res.conflicts().size()));
        for (EventResponse conflict : res.conflicts()) {
            sb.append(String.format("- \"%s\" [ID: %d] from %s to %s\n",
                    conflict.title(), conflict.id(), conflict.startAt(), conflict.endAt()));
        }
        return McpProtocol.McpToolResult.success(sb.toString().trim());
    }

    /**
     * Resolves a calendar from an argument (which can be a calendar name, numeric ID, or null).
     * If null, picks the user's default calendar.
     * If not found, throws a friendly exception listing available calendars.
     */
    private CalendarResponse resolveTargetCalendar(List<CalendarResponse> calendars, Object calendarArg) {
        if (calendars.isEmpty()) {
            throw new NotFoundException("No calendars found for user. Please create a calendar first.");
        }

        if (calendarArg == null || calendarArg.toString().isBlank()) {
            return calendars.stream()
                    .filter(CalendarResponse::isDefault)
                    .findFirst()
                    .orElse(calendars.getFirst());
        }

        String search = calendarArg.toString().trim();

        // 1. Try parsing as numeric ID
        try {
            long targetId = Long.parseLong(search);
            Optional<CalendarResponse> match = calendars.stream().filter(c -> c.id().equals(targetId)).findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        } catch (NumberFormatException ignored) {}

        // 2. Try matching by name (case-insensitive)
        Optional<CalendarResponse> nameMatch = calendars.stream()
                .filter(c -> c.name().equalsIgnoreCase(search))
                .findFirst();

        if (nameMatch.isPresent()) {
            return nameMatch.get();
        }

        // 3. Fallback: partial match
        Optional<CalendarResponse> partialMatch = calendars.stream()
                .filter(c -> c.name().toLowerCase().contains(search.toLowerCase()))
                .findFirst();

        if (partialMatch.isPresent()) {
            return partialMatch.get();
        }

        String available = calendars.stream().map(c -> "\"" + c.name() + "\"").collect(Collectors.joining(", "));
        throw new NotFoundException(String.format("Calendar \"%s\" not found. Available calendars: %s", search, available));
    }

    private McpProtocol.McpTool createTool(String name, String description, Map<String, Object> inputSchema) {
        return new McpProtocol.McpTool(name, description, inputSchema);
    }

    private String getString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private Long getLong(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(val.toString().trim());
    }

    private List<Long> getLongList(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof List<?> list) {
            List<Long> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number n) {
                    result.add(n.longValue());
                } else if (item != null) {
                    result.add(Long.parseLong(item.toString().trim()));
                }
            }
            return result;
        }
        return null;
    }

    private Instant parseInstant(String text, String fallbackTimeZone) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Timestamp parameter is required.");
        }
        text = text.trim();

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {}

        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {}

        try {
            LocalDateTime ldt = LocalDateTime.parse(text);
            ZoneId zone = (fallbackTimeZone != null && !fallbackTimeZone.isBlank())
                    ? ZoneId.of(fallbackTimeZone)
                    : ZoneId.of("UTC");
            return ldt.atZone(zone).toInstant();
        } catch (Exception ignored) {}

        throw new IllegalArgumentException("Could not parse timestamp: '" + text + "'. Expected ISO-8601 (e.g. '2026-10-05T15:00:00Z').");
    }
}
