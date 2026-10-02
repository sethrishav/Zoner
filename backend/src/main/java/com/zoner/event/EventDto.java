package com.zoner.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class EventDto {

    private EventDto() {}

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
            List<ReminderDto> reminders
    ) {}

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
            Long version,
            List<ReminderDto> reminders
    ) {}

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
            Long excludeEventId
    ) {}

    public record AvailabilityResponse(
            boolean available,
            List<EventResponse> conflicts
    ) {}
}
