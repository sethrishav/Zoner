package com.zoner.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public final class EventDto {

    private EventDto() {}

    public enum RecurrenceEditMode {
        THIS,
        THIS_AND_FOLLOWING,
        ALL
    }

    public record ReminderDto(
            Long id,
            @NotNull(message = "Minutes before is required")
            @Min(value = 0, message = "Minutes before cannot be negative")
            Integer minutesBefore,
            @NotNull(message = "Reminder channel is required")
            ReminderChannel channel
    ) {
        public static ReminderDto from(Reminder reminder) {
            return new ReminderDto(reminder.getId(), reminder.getMinutesBefore(), reminder.getChannel());
        }
    }

    public record CreateEventRequest(
            @NotNull(message = "Calendar ID is required")
            Long calendarId,

            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title must be at most 255 characters")
            String title,

            String description,
            String location,
            String color,
            Boolean allDay,

            @NotNull(message = "Start time is required")
            Instant startAt,

            @NotNull(message = "End time is required")
            Instant endAt,

            String timeZone,
            String recurrenceRule,
            List<ReminderDto> reminders
    ) {
        // Constructor overload for backward compatibility with 9-parameter call without recurrenceRule
        public CreateEventRequest(
                Long calendarId, String title, String description, String location, String color,
                Boolean allDay, Instant startAt, Instant endAt, String timeZone, List<ReminderDto> reminders) {
            this(calendarId, title, description, location, color, allDay, startAt, endAt, timeZone, null, reminders);
        }
    }

    public record UpdateEventRequest(
            Long calendarId,

            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title must be at most 255 characters")
            String title,

            String description,
            String location,
            String color,
            Boolean allDay,

            @NotNull(message = "Start time is required")
            Instant startAt,

            @NotNull(message = "End time is required")
            Instant endAt,

            String timeZone,
            String recurrenceRule,
            RecurrenceEditMode editMode,
            Instant originalStart,
            Long version,
            List<ReminderDto> reminders
    ) {
        // Constructor overload for backward compatibility with M3 calls
        public UpdateEventRequest(
                Long calendarId, String title, String description, String location, String color,
                Boolean allDay, Instant startAt, Instant endAt, String timeZone, Long version, List<ReminderDto> reminders) {
            this(calendarId, title, description, location, color, allDay, startAt, endAt, timeZone, null, RecurrenceEditMode.ALL, null, version, reminders);
        }
    }

    public record EventResponse(
            Long id,
            Long calendarId,
            String calendarName,
            String calendarColor,
            String title,
            String description,
            String location,
            String color,
            boolean allDay,
            Instant startAt,
            Instant endAt,
            LocalDateTime startLocal,
            LocalDateTime endLocal,
            String timeZone,
            String recurrenceRule,
            Instant originalStart,
            boolean recurring,
            boolean exception,
            Long version,
            Long createdBy,
            Instant createdAt,
            Instant updatedAt,
            List<ReminderDto> reminders
    ) {
        public static EventResponse from(Event event) {
            List<ReminderDto> reminderDtos = (event.getReminders() != null)
                    ? event.getReminders().stream().map(ReminderDto::from).toList()
                    : List.of();

            boolean isRecurring = event.getRecurrenceRule() != null && !event.getRecurrenceRule().isBlank();

            return new EventResponse(
                    event.getId(),
                    event.getCalendar().getId(),
                    event.getCalendar().getName(),
                    event.getCalendar().getColor(),
                    event.getTitle(),
                    event.getDescription(),
                    event.getLocation(),
                    event.getColor() != null ? event.getColor() : event.getCalendar().getColor(),
                    event.isAllDay(),
                    event.getStartAt(),
                    event.getEndAt(),
                    event.getStartLocal(),
                    event.getEndLocal(),
                    event.getTimeZone(),
                    event.getRecurrenceRule(),
                    null, // originalStart only set on occurrences
                    isRecurring,
                    false,
                    event.getVersion(),
                    event.getCreatedBy() != null ? event.getCreatedBy().getId() : null,
                    event.getCreatedAt(),
                    event.getUpdatedAt(),
                    reminderDtos
            );
        }

        public static EventResponse fromOccurrence(Event event, Instant occStart, EventException exception) {
            List<ReminderDto> reminderDtos = (event.getReminders() != null)
                    ? event.getReminders().stream().map(ReminderDto::from).toList()
                    : List.of();

            boolean isRecurring = event.getRecurrenceRule() != null && !event.getRecurrenceRule().isBlank();
            ZoneId zoneId = ZoneId.of(event.getTimeZone() != null ? event.getTimeZone() : "UTC");

            if (exception != null && exception.getExceptionType() == ExceptionType.MODIFIED) {
                String title = exception.getOverrideTitle() != null ? exception.getOverrideTitle() : event.getTitle();
                String desc = exception.getOverrideDesc() != null ? exception.getOverrideDesc() : event.getDescription();
                String loc = exception.getOverrideLocation() != null ? exception.getOverrideLocation() : event.getLocation();
                String col = exception.getOverrideColor() != null ? exception.getOverrideColor() : event.getColor();
                boolean allDay = exception.getOverrideAllDay() != null ? exception.getOverrideAllDay() : event.isAllDay();

                Instant start = exception.getOverrideStartAt() != null ? exception.getOverrideStartAt() : occStart;
                Instant end = exception.getOverrideEndAt() != null
                        ? exception.getOverrideEndAt()
                        : start.plus(Duration.between(event.getStartAt(), event.getEndAt()));

                return new EventResponse(
                        event.getId(),
                        event.getCalendar().getId(),
                        event.getCalendar().getName(),
                        event.getCalendar().getColor(),
                        title,
                        desc,
                        loc,
                        col != null ? col : event.getCalendar().getColor(),
                        allDay,
                        start,
                        end,
                        LocalDateTime.ofInstant(start, zoneId),
                        LocalDateTime.ofInstant(end, zoneId),
                        event.getTimeZone(),
                        event.getRecurrenceRule(),
                        occStart,
                        isRecurring,
                        true,
                        event.getVersion(),
                        event.getCreatedBy() != null ? event.getCreatedBy().getId() : null,
                        event.getCreatedAt(),
                        event.getUpdatedAt(),
                        reminderDtos
                );
            }

            Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
            Instant occEnd = occStart.plus(duration);

            return new EventResponse(
                    event.getId(),
                    event.getCalendar().getId(),
                    event.getCalendar().getName(),
                    event.getCalendar().getColor(),
                    event.getTitle(),
                    event.getDescription(),
                    event.getLocation(),
                    event.getColor() != null ? event.getColor() : event.getCalendar().getColor(),
                    event.isAllDay(),
                    occStart,
                    occEnd,
                    LocalDateTime.ofInstant(occStart, zoneId),
                    LocalDateTime.ofInstant(occEnd, zoneId),
                    event.getTimeZone(),
                    event.getRecurrenceRule(),
                    isRecurring ? occStart : null,
                    isRecurring,
                    false,
                    event.getVersion(),
                    event.getCreatedBy() != null ? event.getCreatedBy().getId() : null,
                    event.getCreatedAt(),
                    event.getUpdatedAt(),
                    reminderDtos
            );
        }
    }

    public record AvailabilityRequest(
            @NotNull(message = "From time is required")
            Instant from,

            @NotNull(message = "To time is required")
            Instant to,

            List<Long> calendarIds,
            Long excludeEventId,
            Instant excludeOriginalStart
    ) {
        public AvailabilityRequest(Instant from, Instant to, List<Long> calendarIds, Long excludeEventId) {
            this(from, to, calendarIds, excludeEventId, null);
        }
    }

    public record AvailabilityResponse(
            boolean available,
            List<EventResponse> conflicts
    ) {}
}
