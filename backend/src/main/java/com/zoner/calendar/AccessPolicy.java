package com.zoner.calendar;

import com.zoner.common.error.ForbiddenException;
import com.zoner.common.error.NotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for calendar and event authorization.
 *
 * Matrix:
 * | Action                     | OWNER | EDIT  | VIEW  | No relationship |
 * |----------------------------|-------|-------|-------|-----------------|
 * | Read events / calendar     |  OK   |  OK   |  OK   | 404 Not Found   |
 * | Create/update/delete event |  OK   |  OK   | 403   | 404 Not Found   |
 * | Rename/delete calendar     |  OK   | 403   | 403   | 404 Not Found   |
 * | Manage shares              |  OK   | 403   | 403   | 404 Not Found   |
 *
 * To prevent resource enumeration attacks, a 404 Not Found is ALWAYS returned
 * if the user has no relationship to the calendar, rather than leaking its existence with 403.
 */
@Component
public class AccessPolicy {

    private final CalendarRepository calendarRepository;
    private final CalendarShareRepository calendarShareRepository;

    public record CalendarAccess(Calendar calendar, SharePermission permission) {}

    public AccessPolicy(
            CalendarRepository calendarRepository,
            CalendarShareRepository calendarShareRepository) {
        this.calendarRepository = calendarRepository;
        this.calendarShareRepository = calendarShareRepository;
    }

    /**
     * Resolves the user's relationship with the calendar.
     * Returns 404 if the calendar does not exist or the user has no access.
     */
    public CalendarAccess getAccess(Long userId, Long calendarId) {
        Calendar calendar = calendarRepository.findByIdWithOwner(calendarId)
                .orElseThrow(() -> new NotFoundException("Calendar not found."));

        if (calendar.getOwner().getId().equals(userId)) {
            return new CalendarAccess(calendar, SharePermission.OWNER);
        }

        Optional<CalendarShare> share = calendarShareRepository.findByCalendarIdAndUserId(calendarId, userId);
        if (share.isPresent()) {
            return new CalendarAccess(calendar, share.get().getPermission());
        }

        // Return 404 instead of 403 so existence of the calendar is not leaked
        throw new NotFoundException("Calendar not found.");
    }

    /**
     * Asserts that the user has at least the required permission.
     * Returns the calendar and resolved permission.
     */
    public CalendarAccess requireAccess(Long userId, Long calendarId, SharePermission minimumRequired) {
        CalendarAccess access = getAccess(userId, calendarId);

        if (minimumRequired == SharePermission.OWNER && access.permission() != SharePermission.OWNER) {
            throw new ForbiddenException("Only the calendar owner can perform this action.");
        }

        if (minimumRequired == SharePermission.EDIT && !access.permission().canEditEvents()) {
            throw new ForbiddenException("You have view-only access to this calendar.");
        }

        return access;
    }
}
