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

    private final EventRepository eventRepository;
    private final AccessPolicy accessPolicy;

    public AvailabilityService(EventRepository eventRepository, AccessPolicy accessPolicy) {
        this.eventRepository = eventRepository;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Checks availability across the specified calendars (or all accessible calendars by default)
     * using the standard interval overlap rule: startA < endB AND endA > startB.
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

        List<Event> conflicts = (request.excludeEventId() != null)
                ? eventRepository.findConflictingEventsExcluding(targetCalendarIds, request.from(), request.to(), request.excludeEventId())
                : eventRepository.findConflictingEvents(targetCalendarIds, request.from(), request.to());

        List<EventResponse> conflictResponses = conflicts.stream()
                .map(EventResponse::from)
                .toList();

        return new AvailabilityResponse(conflictResponses.isEmpty(), conflictResponses);
    }

    /**
     * Programmatic check whether an interval overlaps.
     */
    public boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }
}
