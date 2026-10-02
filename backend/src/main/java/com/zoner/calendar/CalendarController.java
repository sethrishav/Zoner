package com.zoner.calendar;

import com.zoner.auth.CurrentUser;
import com.zoner.auth.UserPrincipal;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.calendar.CalendarDto.CalendarShareResponse;
import com.zoner.calendar.CalendarDto.CreateCalendarRequest;
import com.zoner.calendar.CalendarDto.ShareCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdateCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdatePreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendars")
@Tag(name = "Calendars & Sharing", description = "Multiple calendar management, visibility preferences, and permission-based sharing")
@SecurityRequirement(name = "bearerAuth")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    @Operation(summary = "List all calendars accessible to the user (owned and shared)")
    public ResponseEntity<List<CalendarResponse>> listCalendars(@CurrentUser UserPrincipal principal) {
        List<CalendarResponse> calendars = calendarService.listCalendars(principal.getId());
        return ResponseEntity.ok(calendars);
    }

    @PostMapping
    @Operation(summary = "Create a new custom calendar")
    public ResponseEntity<CalendarResponse> createCalendar(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateCalendarRequest request) {
        CalendarResponse response = calendarService.createCalendar(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get calendar details by ID")
    public ResponseEntity<CalendarResponse> getCalendar(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {
        CalendarResponse response = calendarService.getCalendar(principal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update calendar name, description, or color (Owner only)")
    public ResponseEntity<CalendarResponse> updateCalendar(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCalendarRequest request) {
        CalendarResponse response = calendarService.updateCalendar(principal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a calendar (Owner only, cannot delete default calendar)")
    public void deleteCalendar(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {
        calendarService.deleteCalendar(principal.getId(), id);
    }

    @PatchMapping("/{id}/preference")
    @Operation(summary = "Update user visibility/color preference for this calendar")
    public ResponseEntity<CalendarResponse> updatePreference(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        CalendarResponse response = calendarService.updatePreference(principal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/shares")
    @Operation(summary = "List all users this calendar is shared with (Owner only)")
    public ResponseEntity<List<CalendarShareResponse>> listShares(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {
        List<CalendarShareResponse> shares = calendarService.listShares(principal.getId(), id);
        return ResponseEntity.ok(shares);
    }

    @PostMapping("/{id}/shares")
    @Operation(summary = "Share calendar with another registered user by email with VIEW or EDIT permission")
    public ResponseEntity<CalendarShareResponse> shareCalendar(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ShareCalendarRequest request) {
        CalendarShareResponse response = calendarService.shareCalendar(principal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/shares/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke calendar sharing for a user (Owner or self-unsharing)")
    public void removeShare(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long userId) {
        calendarService.removeShare(principal.getId(), id, userId);
    }
}
