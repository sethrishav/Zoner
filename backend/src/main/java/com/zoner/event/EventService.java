package com.zoner.event;

import com.zoner.auth.User;
import com.zoner.auth.UserRepository;
import com.zoner.calendar.AccessPolicy;
import com.zoner.calendar.AccessPolicy.CalendarAccess;
import com.zoner.calendar.SharePermission;
import com.zoner.common.error.BusinessRuleException;
import com.zoner.common.error.NotFoundException;
import com.zoner.event.EventDto.CreateEventRequest;
import com.zoner.event.EventDto.EventResponse;
import com.zoner.event.EventDto.ReminderDto;
import com.zoner.event.EventDto.UpdateEventRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;

    public EventService(
            EventRepository eventRepository,
            UserRepository userRepository,
            AccessPolicy accessPolicy) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public EventResponse createEvent(Long userId, CreateEventRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessRuleException("End time must be after start time.");
        }

        CalendarAccess access = accessPolicy.requireAccess(userId, request.calendarId(), SharePermission.EDIT);
        User creator = userRepository.getReferenceById(userId);

        String timeZone = request.timeZone();
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = creator.getTimeZone() != null ? creator.getTimeZone() : "UTC";
        }
        validateTimeZone(timeZone);

        boolean allDay = Boolean.TRUE.equals(request.allDay());
        Event event = new Event(
                access.calendar(),
                request.title().trim(),
                request.description(),
                request.location(),
                request.color(),
                allDay,
                request.startAt(),
                request.endAt(),
                timeZone,
                creator
        );

        if (request.reminders() != null) {
            for (ReminderDto r : request.reminders()) {
                event.addReminder(r.minutesBefore(), r.channel());
            }
        }

        Event saved = eventRepository.save(event);
        return EventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdWithCalendarAndReminders(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found."));

        try {
            accessPolicy.requireAccess(userId, event.getCalendar().getId(), SharePermission.VIEW);
        } catch (NotFoundException e) {
            // Anti-enumeration: stranger receives "Event not found" with 404
            throw new NotFoundException("Event not found.");
        }

        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateEvent(Long userId, Long eventId, UpdateEventRequest request) {
        Event event = eventRepository.findByIdWithCalendarAndReminders(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found."));

        try {
            accessPolicy.requireAccess(userId, event.getCalendar().getId(), SharePermission.EDIT);
        } catch (NotFoundException e) {
            throw new NotFoundException("Event not found.");
        }

        // If changing calendars, verify EDIT permission on target calendar as well
        if (request.calendarId() != null && !request.calendarId().equals(event.getCalendar().getId())) {
            CalendarAccess targetCalAccess = accessPolicy.requireAccess(userId, request.calendarId(), SharePermission.EDIT);
            event.setCalendar(targetCalAccess.calendar());
        }

        // Optimistic locking check
        if (request.version() != null && !request.version().equals(event.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Event.class, eventId);
        }

        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessRuleException("End time must be after start time.");
        }

        String timeZone = request.timeZone();
        if (timeZone != null && !timeZone.isBlank()) {
            validateTimeZone(timeZone);
            event.setTimeZone(timeZone);
        }

        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setColor(request.color());
        event.setAllDay(Boolean.TRUE.equals(request.allDay()));
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.recalculateLocalTimes();

        if (request.reminders() != null) {
            event.clearReminders();
            for (ReminderDto r : request.reminders()) {
                event.addReminder(r.minutesBefore(), r.channel());
            }
        }

        Event saved = eventRepository.saveAndFlush(event);
        return EventResponse.from(saved);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdWithCalendarAndReminders(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found."));

        try {
            accessPolicy.requireAccess(userId, event.getCalendar().getId(), SharePermission.EDIT);
        } catch (NotFoundException e) {
            throw new NotFoundException("Event not found.");
        }

        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEventsInRange(
            Long userId, Instant from, Instant to, List<Long> requestedCalendarIds) {
        if (!to.isAfter(from)) {
            throw new BusinessRuleException("End time 'to' must be after start time 'from'.");
        }

        if (Duration.between(from, to).toDays() > 366) {
            throw new BusinessRuleException("Query range cannot exceed 366 days.");
        }

        List<Long> targetCalendarIds;
        if (requestedCalendarIds != null && !requestedCalendarIds.isEmpty()) {
            for (Long calId : requestedCalendarIds) {
                accessPolicy.requireAccess(userId, calId, SharePermission.VIEW);
            }
            targetCalendarIds = requestedCalendarIds;
        } else {
            targetCalendarIds = accessPolicy.getEnabledCalendarIds(userId);
        }

        if (targetCalendarIds.isEmpty()) {
            return List.of();
        }

        List<Event> events = eventRepository.findEventsInRange(targetCalendarIds, from, to);
        return events.stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> searchEvents(Long userId, String query, Instant from, Instant to, int limit) {
        if (query == null || query.trim().isBlank()) {
            return List.of();
        }

        List<Long> accessibleIds = accessPolicy.getAccessibleCalendarIds(userId, SharePermission.VIEW);
        if (accessibleIds.isEmpty()) {
            return List.of();
        }

        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, cappedLimit);

        List<Event> results;
        if (from != null && to != null) {
            results = eventRepository.searchEventsWithRange(accessibleIds, query.trim(), from, to, pageable);
        } else if (from != null) {
            results = eventRepository.searchEventsFrom(accessibleIds, query.trim(), from, pageable);
        } else if (to != null) {
            results = eventRepository.searchEventsTo(accessibleIds, query.trim(), to, pageable);
        } else {
            results = eventRepository.searchEvents(accessibleIds, query.trim(), pageable);
        }
        return results.stream().map(EventResponse::from).toList();
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (Exception e) {
            throw new BusinessRuleException("Invalid IANA time zone: '" + timeZone + "'.");
        }
    }
}
