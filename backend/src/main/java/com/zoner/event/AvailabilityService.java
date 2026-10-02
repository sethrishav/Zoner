package com.zoner.event;

import com.zoner.calendar.AccessPolicy;
import com.zoner.calendar.SharePermission;
import com.zoner.common.error.BusinessRuleException;
import com.zoner.event.EventDto.AvailabilityRequest;
import com.zoner.event.EventDto.AvailabilityResponse;
import com.zoner.event.EventDto.EventResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private final EventService eventService;
    private final AccessPolicy accessPolicy;

    public AvailabilityService(EventService eventService, AccessPolicy accessPolicy) {
        this.eventService = eventService;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Checks availability across the specified calendars (or all accessible calendars by default)
     * using the standard interval overlap rule: startA < endB AND endA > startB.
     * Fully recurrence-aware: expands recurring events and respects exceptions.
     * Note: Edge-touching intervals (startA == endB or endA == startB) do NOT conflict.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(Long userId, AvailabilityRequest request) {
        if (!request.to().isAfter(request.from())) {
            throw new BusinessRuleException("End time must be after start time.");
        }

        List<Long> targetCalendarIds;
        if (request.calendarIds() != null && !request.calendarIds().isEmpty()) {
            for (Long calId : request.calendarIds()) {
                accessPolicy.requireAccess(userId, calId, SharePermission.VIEW);
            }
            targetCalendarIds = request.calendarIds();
        } else {
            targetCalendarIds = accessPolicy.getAccessibleCalendarIds(userId, SharePermission.VIEW);
        }

        if (targetCalendarIds.isEmpty()) {
            return new AvailabilityResponse(true, List.of());
        }

        List<EventResponse> occurrences = eventService.listEventsInRange(
                userId, request.from(), request.to(), targetCalendarIds);

        List<EventResponse> conflicts = occurrences.stream()
                .filter(e -> {
                    if (request.excludeEventId() != null && e.id().equals(request.excludeEventId())) {
                        if (request.excludeOriginalStart() != null) {
                            return !request.excludeOriginalStart().equals(e.originalStart());
                        }
                        return false;
                    }
                    return true;
                })
                .filter(e -> overlaps(request.from(), request.to(), e.startAt(), e.endAt()))
                .toList();

        return new AvailabilityResponse(conflicts.isEmpty(), conflicts);
    }

    /**
     * Programmatic check whether an interval overlaps.
     */
    public boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }
}
