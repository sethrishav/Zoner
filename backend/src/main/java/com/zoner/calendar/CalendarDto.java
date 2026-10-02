package com.zoner.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Consolidated DTOs for calendar management, preferences, and sharing.
 */
public final class CalendarDto {

    private CalendarDto() {}

    public record CreateCalendarRequest(
            @Schema(description = "Calendar name", example = "Work")
            @NotBlank(message = "Calendar name is required")
            @Size(max = 100, message = "Name must not exceed 100 characters")
            String name,

            @Schema(description = "Calendar description", example = "Project deadlines and meetings")
            @Size(max = 500, message = "Description must not exceed 500 characters")
            String description,

            @Schema(description = "Hex color representation", example = "#10B981")
            String color
    ) {}

    public record UpdateCalendarRequest(
            @Schema(description = "Calendar name", example = "Work Projects")
            @NotBlank(message = "Calendar name is required")
            @Size(max = 100, message = "Name must not exceed 100 characters")
            String name,

            @Schema(description = "Calendar description", example = "Updated project calendar")
            @Size(max = 500, message = "Description must not exceed 500 characters")
            String description,

            @Schema(description = "Hex color representation", example = "#059669")
            String color
    ) {}

    public record UpdatePreferenceRequest(
            @Schema(description = "Whether the calendar is visible/enabled in UI views", example = "true")
            Boolean enabled,

            @Schema(description = "Optional user color override for this calendar", example = "#EF4444")
            String colorOverride
    ) {}

    public record ShareCalendarRequest(
            @Schema(description = "Registered user's email address to share with", example = "colleague@example.com")
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @Schema(description = "Permission level granted (VIEW or EDIT)", example = "EDIT")
            @NotNull(message = "Permission is required")
            SharePermission permission
    ) {}

    public record CalendarOwnerSummary(
            Long id,
            String email,
            String displayName
    ) {}

    public record CalendarShareResponse(
            Long userId,
            String email,
            String displayName,
            SharePermission permission,
            Instant createdAt
    ) {
        public static CalendarShareResponse from(CalendarShare share) {
            return new CalendarShareResponse(
                    share.getUser().getId(),
                    share.getUser().getEmail(),
                    share.getUser().getDisplayName(),
                    share.getPermission(),
                    share.getCreatedAt()
            );
        }
    }

    public record CalendarResponse(
            Long id,
            String name,
            String description,
            String color,
            boolean isDefault,
            SharePermission permission,
            boolean enabled,
            CalendarOwnerSummary owner,
            Instant createdAt
    ) {
        public static CalendarResponse of(
                Calendar calendar,
                SharePermission permission,
                boolean enabled,
                String effectiveColor) {
            return new CalendarResponse(
                    calendar.getId(),
                    calendar.getName(),
                    calendar.getDescription(),
                    effectiveColor != null ? effectiveColor : calendar.getColor(),
                    calendar.isDefault(),
                    permission,
                    enabled,
                    new CalendarOwnerSummary(
                            calendar.getOwner().getId(),
                            calendar.getOwner().getEmail(),
                            calendar.getOwner().getDisplayName()
                    ),
                    calendar.getCreatedAt()
            );
        }
    }
}
