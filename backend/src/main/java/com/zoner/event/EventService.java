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
import com.zoner.event.EventDto.RecurrenceEditMode;
import com.zoner.event.EventDto.ReminderDto;
import com.zoner.event.EventDto.UpdateEventRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final EventExceptionRepository eventExceptionRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;
    private final RecurrenceExpander recurrenceExpander;

    public EventService(
            EventRepository eventRepository,
            EventExceptionRepository eventExceptionRepository,
            UserRepository userRepository,
            AccessPolicy accessPolicy,
            RecurrenceExpander recurrenceExpander) {
        this.eventRepository = eventRepository;
        this.eventExceptionRepository = eventExceptionRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.recurrenceExpander = recurrenceExpander;
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

        if (request.recurrenceRule() != null && !request.recurrenceRule().isBlank()) {
            String rule = request.recurrenceRule().trim();
            recurrenceExpander.validateRule(rule);
            event.setRecurrenceRule(rule);
            event.setRecurrenceUntil(recurrenceExpander.parseUntil(rule, timeZone));
        }

        if (request.reminders() != null) {
            for (ReminderDto r : request.reminders()) {
                event.addReminder(r.minutesBefore(), r.channel());
            }
        }

        if (request.attendees() != null) {
            for (EventDto.AttendeeDto a : request.attendees()) {
                if (a.email() != null && !a.email().isBlank()) {
                    event.addAttendee(a.email(), a.displayName(), a.status());
                }
            }
        }

        Event saved = eventRepository.save(event);
        log.info("[EVENT CREATED] eventId={}, title='{}', startAt={}, remindersCount={}, reminders={}",
                saved.getId(), saved.getTitle(), saved.getStartAt(),
                saved.getReminders().size(),
                saved.getReminders().stream().map(r -> r.getMinutesBefore() + "m (" + r.getChannel() + ")").toList());
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

        RecurrenceEditMode editMode = request.editMode() != null
                ? request.editMode()
                : RecurrenceEditMode.ALL;

        // If this is a recurring event and caller wants to edit a single occurrence
        if (event.getRecurrenceRule() != null && !event.getRecurrenceRule().isBlank() && editMode == RecurrenceEditMode.THIS) {
            if (request.originalStart() == null) {
                throw new BusinessRuleException("originalStart is required when editing a single occurrence (editMode=THIS).");
            }
            if (!request.endAt().isAfter(request.startAt())) {
                throw new BusinessRuleException("End time must be after start time.");
            }

            EventException ex = eventExceptionRepository.findByEventIdAndOriginalStart(eventId, request.originalStart())
                    .orElseGet(() -> new EventException(event, request.originalStart(), ExceptionType.MODIFIED));

            ex.setExceptionType(ExceptionType.MODIFIED);
            ex.setOverrideTitle(request.title().trim());
            ex.setOverrideDesc(request.description());
            ex.setOverrideLocation(request.location());
            ex.setOverrideColor(request.color());
            ex.setOverrideStartAt(request.startAt());
            ex.setOverrideEndAt(request.endAt());
            ex.setOverrideAllDay(Boolean.TRUE.equals(request.allDay()));

            ex = eventExceptionRepository.save(ex);
            log.info("[EVENT EXCEPTION SAVED] eventId={}, originalStart={}, exceptionId={}, title='{}', type={}",
                    eventId, request.originalStart(), ex.getId(), ex.getOverrideTitle(), ex.getExceptionType());
            return EventResponse.fromOccurrence(event, request.originalStart(), ex);
        }

        // If caller wants to split this series from originalStart onward (THIS_AND_FOLLOWING)
        if (event.getRecurrenceRule() != null && !event.getRecurrenceRule().isBlank() && editMode == RecurrenceEditMode.THIS_AND_FOLLOWING) {
            if (request.originalStart() == null) {
                throw new BusinessRuleException("originalStart is required when splitting a series (editMode=THIS_AND_FOLLOWING).");
            }

            // 1. Truncate master event series right before originalStart
            event.setRecurrenceUntil(request.originalStart().minusMillis(1));
            eventRepository.saveAndFlush(event);

            // 2. Create new series starting at request.startAt()
            CalendarAccess targetAccess = (request.calendarId() != null && !request.calendarId().equals(event.getCalendar().getId()))
                    ? accessPolicy.requireAccess(userId, request.calendarId(), SharePermission.EDIT)
                    : new CalendarAccess(event.getCalendar(), SharePermission.EDIT);

            String timeZone = request.timeZone() != null && !request.timeZone().isBlank()
                    ? request.timeZone()
                    : event.getTimeZone();
            validateTimeZone(timeZone);

            String rule = request.recurrenceRule() != null && !request.recurrenceRule().isBlank()
                    ? request.recurrenceRule().trim()
                    : event.getRecurrenceRule();
            recurrenceExpander.validateRule(rule);

            Event newSeries = new Event(
                    targetAccess.calendar(),
                    request.title().trim(),
                    request.description(),
                    request.location(),
                    request.color(),
                    Boolean.TRUE.equals(request.allDay()),
                    request.startAt(),
                    request.endAt(),
                    timeZone,
                    event.getCreatedBy()
            );
            newSeries.setRecurrenceRule(rule);
            newSeries.setRecurrenceUntil(recurrenceExpander.parseUntil(rule, timeZone));

            if (request.reminders() != null) {
                for (ReminderDto r : request.reminders()) {
                    newSeries.addReminder(r.minutesBefore(), r.channel());
                }
            }
            if (request.attendees() != null) {
                for (EventDto.AttendeeDto a : request.attendees()) {
                    if (a.email() != null && !a.email().isBlank()) {
                        newSeries.addAttendee(a.email(), a.displayName(), a.status());
                    }
                }
            }

            Event savedNewSeries = eventRepository.save(newSeries);
            return EventResponse.from(savedNewSeries);
        }

        // Mode ALL: updates the master event
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

        if (request.recurrenceRule() != null) {
            String rule = request.recurrenceRule().trim();
            if (rule.isBlank()) {
                event.setRecurrenceRule(null);
                event.setRecurrenceUntil(null);
                eventExceptionRepository.deleteAllByEventId(eventId);
            } else {
                recurrenceExpander.validateRule(rule);
                event.setRecurrenceRule(rule);
                event.setRecurrenceUntil(recurrenceExpander.parseUntil(rule, event.getTimeZone()));
            }
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

        if (request.attendees() != null) {
            event.clearAttendees();
            eventRepository.saveAndFlush(event); // Ensure orphan deletion is flushed before re-inserting
            for (EventDto.AttendeeDto a : request.attendees()) {
                if (a.email() != null && !a.email().isBlank()) {
                    event.addAttendee(a.email(), a.displayName(), a.status());
                }
            }
        }

        Event saved = eventRepository.saveAndFlush(event);
        log.info("[EVENT UPDATED] eventId={}, title='{}', startAt={}, remindersCount={}, reminders={}",
                saved.getId(), saved.getTitle(), saved.getStartAt(),
                saved.getReminders().size(),
                saved.getReminders().stream().map(r -> r.getMinutesBefore() + "m (" + r.getChannel() + ")").toList());
        return EventResponse.from(saved);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        deleteEvent(userId, eventId, RecurrenceEditMode.ALL, null);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId, RecurrenceEditMode editMode, Instant originalStart) {
        Event event = eventRepository.findByIdWithCalendarAndReminders(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found."));

        try {
            accessPolicy.requireAccess(userId, event.getCalendar().getId(), SharePermission.EDIT);
        } catch (NotFoundException e) {
            throw new NotFoundException("Event not found.");
        }

        if (editMode == null) {
            editMode = RecurrenceEditMode.ALL;
        }

        if (event.getRecurrenceRule() != null && !event.getRecurrenceRule().isBlank()) {
            if (editMode == RecurrenceEditMode.THIS) {
                if (originalStart == null) {
                    throw new BusinessRuleException("originalStart is required when deleting a single occurrence (editMode=THIS).");
                }
                EventException ex = eventExceptionRepository.findByEventIdAndOriginalStart(eventId, originalStart)
                        .orElseGet(() -> new EventException(event, originalStart, ExceptionType.CANCELLED));
                ex.setExceptionType(ExceptionType.CANCELLED);
                eventExceptionRepository.save(ex);
                log.info("[EVENT OCCURRENCE CANCELLED] eventId={}, userId={}, originalStart={}", eventId, userId, originalStart);
                return;
            }

            if (editMode == RecurrenceEditMode.THIS_AND_FOLLOWING) {
                if (originalStart == null) {
                    throw new BusinessRuleException("originalStart is required when truncating a series (editMode=THIS_AND_FOLLOWING).");
                }
                event.setRecurrenceUntil(originalStart.minusMillis(1));
                eventRepository.save(event);
                log.info("[EVENT SERIES TRUNCATED] eventId={}, userId={}, until={}", eventId, userId, originalStart.minusMillis(1));
                return;
            }
        }

        // Mode ALL: deletes master event and cascades to exceptions
        eventRepository.delete(event);
        log.info("[EVENT DELETED] eventId={}, userId={}, title='{}', editMode={}", event.getId(), userId, event.getTitle(), editMode);
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

        // 1. Non-recurring events
        List<Event> nonRecurring = eventRepository.findNonRecurringEventsInRange(targetCalendarIds, from, to);
        List<EventResponse> results = new ArrayList<>();
        for (Event e : nonRecurring) {
            results.add(EventResponse.from(e));
        }

        // 2. Recurring event candidates
        List<Event> recurringCandidates = eventRepository.findRecurringEventsCandidates(targetCalendarIds, from, to);
        if (!recurringCandidates.isEmpty()) {
            List<Long> recurringIds = recurringCandidates.stream().map(Event::getId).toList();
            List<EventException> exceptions = eventExceptionRepository.findAllByEventIdIn(recurringIds);

            // Group exceptions by eventId -> originalStart -> EventException
            Map<Long, Map<Instant, EventException>> exceptionMap = new HashMap<>();
            for (EventException ex : exceptions) {
                exceptionMap.computeIfAbsent(ex.getEvent().getId(), k -> new HashMap<>())
                        .put(ex.getOriginalStart(), ex);
            }

            for (Event e : recurringCandidates) {
                List<Instant> occStarts = recurrenceExpander.expand(e, from, to, 500);
                Map<Instant, EventException> eventExceptions = exceptionMap.getOrDefault(e.getId(), Map.of());

                for (Instant occStart : occStarts) {
                    EventException ex = eventExceptions.get(occStart);
                    if (ex != null && ex.getExceptionType() == ExceptionType.CANCELLED) {
                        // Skip cancelled occurrence
                        continue;
                    }
                    results.add(EventResponse.fromOccurrence(e, occStart, ex));
                }
            }
        }

        // 3. Sort chronologically by startAt
        results.sort(Comparator.comparing(EventResponse::startAt));
        return results;
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

    @Transactional
    public EventResponse rsvpEvent(Long userId, Long eventId, AttendeeStatus status) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        boolean hasCalendarAccess = false;
        try {
            accessPolicy.requireAccess(userId, event.getCalendar().getId(), SharePermission.VIEW);
            hasCalendarAccess = true;
        } catch (Exception ignored) {}

        String userEmail = user.getEmail().trim().toLowerCase();
        EventAttendee existingAttendee = event.getAttendees().stream()
                .filter(a -> a.getEmail().equalsIgnoreCase(userEmail))
                .findFirst()
                .orElse(null);

        if (!hasCalendarAccess && existingAttendee == null) {
            throw new NotFoundException("Event not found.");
        }

        if (existingAttendee != null) {
            existingAttendee.setStatus(status);
            if (existingAttendee.getDisplayName() == null || existingAttendee.getDisplayName().isBlank()) {
                existingAttendee.setDisplayName(user.getDisplayName());
            }
        } else {
            event.addAttendee(userEmail, user.getDisplayName(), status);
        }

        Event saved = eventRepository.saveAndFlush(event);
        return EventResponse.from(saved);
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (Exception e) {
            throw new BusinessRuleException("Invalid IANA time zone: '" + timeZone + "'.");
        }
    }
}
