package com.zoner.event;

import com.zoner.auth.CurrentUser;
import com.zoner.auth.UserPrincipal;
import com.zoner.event.EventDto.AvailabilityRequest;
import com.zoner.event.EventDto.AvailabilityResponse;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.UpdateEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events & Availability", description = "Non-recurring event CRUD, range queries, conflict checks, and search")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;
    private final AvailabilityService availabilityService;

    public EventController(EventService eventService, AvailabilityService availabilityService) {
        this.eventService = eventService;
        this.availabilityService = availabilityService;
    }

    @PostMapping
    @Operation(summary = "Create a new event in an accessible calendar (requires EDIT permission)")
    public ResponseEntity<EventResponse> createEvent(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID (requires VIEW permission on the event's calendar)")
    public ResponseEntity<EventResponse> getEvent(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {
        EventResponse response = eventService.getEvent(principal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an event (requires EDIT permission; protected by optimistic locking)")
    public ResponseEntity<EventResponse> updateEvent(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request) {
        EventResponse response = eventService.updateEvent(principal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/rsvp")
    @Operation(summary = "Respond to event RSVP (ACCEPTED, DECLINED, TENTATIVE)")
    public ResponseEntity<EventResponse> rsvpEvent(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody EventDto.RsvpRequest request) {
        EventResponse response = eventService.rsvpEvent(principal.getId(), id, request.status());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an event or recurrence occurrence (requires EDIT permission)")
    public void deleteEvent(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam(value = "editMode", defaultValue = "ALL") EventDto.RecurrenceEditMode editMode,
            @RequestParam(value = "originalStart", required = false) Instant originalStart) {
        eventService.deleteEvent(principal.getId(), id, editMode, originalStart);
    }

    @GetMapping
    @Operation(summary = "List events in a time range across accessible enabled calendars")
    public ResponseEntity<List<EventResponse>> listEventsInRange(
            @CurrentUser UserPrincipal principal,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(value = "calendarIds", required = false) List<Long> calendarIds) {
        List<EventResponse> events = eventService.listEventsInRange(principal.getId(), from, to, calendarIds);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/search")
    @Operation(summary = "Search events by title, description, or location across accessible calendars")
    public ResponseEntity<List<EventResponse>> searchEvents(
            @CurrentUser UserPrincipal principal,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "query", required = false) String queryParam,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        String query = (q != null && !q.isBlank()) ? q : (queryParam != null ? queryParam : "");
        List<EventResponse> events = eventService.searchEvents(principal.getId(), query, from, to, limit);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/availability")
    @Operation(summary = "Check availability via query parameters")
    public ResponseEntity<AvailabilityResponse> checkAvailabilityGet(
            @CurrentUser UserPrincipal principal,
            @RequestParam(value = "start", required = false) Instant start,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "end", required = false) Instant end,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "calendarIds", required = false) List<Long> calendarIds,
            @RequestParam(value = "excludeEventId", required = false) Long excludeEventId) {
        Instant effectiveFrom = from != null ? from : start;
        Instant effectiveTo = to != null ? to : end;
        if (effectiveFrom == null || effectiveTo == null) {
            throw new IllegalArgumentException("Start/from and end/to timestamps are required");
        }
        AvailabilityRequest request = new AvailabilityRequest(effectiveFrom, effectiveTo, calendarIds, excludeEventId);
        AvailabilityResponse response = availabilityService.checkAvailability(principal.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/availability")
    @Operation(summary = "Check availability and find conflicting events within a given interval")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody AvailabilityRequest request) {
        AvailabilityResponse response = availabilityService.checkAvailability(principal.getId(), request);
        return ResponseEntity.ok(response);
    }
}
