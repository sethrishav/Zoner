package com.zoner.config;

import com.zoner.auth.AuthDto;
import com.zoner.auth.AuthService;
import com.zoner.auth.User;
import com.zoner.auth.UserRepository;
import com.zoner.calendar.CalendarDto;
import com.zoner.calendar.CalendarRepository;
import com.zoner.calendar.CalendarService;
import com.zoner.calendar.SharePermission;
import com.zoner.event.AttendeeStatus;
import com.zoner.event.EventDto;
import com.zoner.event.EventService;
import com.zoner.event.ReminderChannel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Automatically provisions demo and reviewer accounts, shared calendars, and sample recurring events
 * on first startup if they do not already exist. Completely idempotent.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final CalendarRepository calendarRepository;
    private final CalendarService calendarService;
    private final EventService eventService;

    public DataSeeder(
            UserRepository userRepository,
            AuthService authService,
            CalendarRepository calendarRepository,
            CalendarService calendarService,
            EventService eventService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.calendarRepository = calendarRepository;
        this.calendarService = calendarService;
        this.eventService = eventService;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmailIgnoreCase("demo@zoner.app").isPresent()) {
            log.info("Demo user already exists; skipping data seed.");
            return;
        }

        try {
            log.info("Seeding demo accounts, shared calendars, and sample events...");

            // 1. Create primary demo user
            authService.register(new AuthDto.RegisterRequest(
                    "demo@zoner.app",
                    "Password123!",
                    "Demo Reviewer",
                    "America/New_York"
            ));
            User demoUser = userRepository.findByEmailIgnoreCase("demo@zoner.app").orElseThrow();

            // 2. Create colleague user for sharing demonstrations
            authService.register(new AuthDto.RegisterRequest(
                    "colleague@zoner.app",
                    "Password123!",
                    "Alex Rivera",
                    "America/Los_Angeles"
            ));
            User colleagueUser = userRepository.findByEmailIgnoreCase("colleague@zoner.app").orElseThrow();

            // 3. Create a secondary "Work" calendar for demo user
            CalendarDto.CalendarResponse workCalendar = calendarService.createCalendar(demoUser.getId(), new CalendarDto.CreateCalendarRequest(
                    "Work",
                    "Engineering and sprint meetings",
                    "#6366F1"
            ));

            // 4. Create shared calendars owned by colleague
            CalendarDto.CalendarResponse alphaCalendar = calendarService.createCalendar(colleagueUser.getId(), new CalendarDto.CreateCalendarRequest(
                    "Project Alpha",
                    "Cross-functional launch milestones (Editable by Demo)",
                    "#10B981"
            ));
            calendarService.shareCalendar(colleagueUser.getId(), alphaCalendar.id(), new CalendarDto.ShareCalendarRequest(
                    "demo@zoner.app",
                    SharePermission.EDIT
            ));

            CalendarDto.CalendarResponse allHandsCalendar = calendarService.createCalendar(colleagueUser.getId(), new CalendarDto.CreateCalendarRequest(
                    "Company Announcements",
                    "Organization-wide broadcast events (View Only)",
                    "#F59E0B"
            ));
            calendarService.shareCalendar(colleagueUser.getId(), allHandsCalendar.id(), new CalendarDto.ShareCalendarRequest(
                    "demo@zoner.app",
                    SharePermission.VIEW
            ));

            // 5. Populate sample events
            Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);

            // Event A: Tomorrow's Architecture Review on Work calendar
            Instant tomorrow2pm = now.plus(1, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS);
            eventService.createEvent(demoUser.getId(), new EventDto.CreateEventRequest(
                    workCalendar.id(),
                    "Architecture & MCP Server Review",
                    "Review Model Context Protocol tool adapters, PAT management, and cloud deployment.",
                    "Google Meet: meet.google.com/zoner-sync",
                    workCalendar.color(),
                    false,
                    tomorrow2pm,
                    tomorrow2pm.plus(1, ChronoUnit.HOURS),
                    "America/New_York",
                    null,
                    List.of(new EventDto.ReminderDto(null, 15, ReminderChannel.IN_APP)),
                    List.of(
                            new EventDto.AttendeeDto(null, "demo@zoner.app", "Demo Reviewer", AttendeeStatus.ACCEPTED),
                            new EventDto.AttendeeDto(null, "colleague@zoner.app", "Alex Rivera", AttendeeStatus.PENDING),
                            new EventDto.AttendeeDto(null, "lead-architect@zoner.app", "Lead Architect", AttendeeStatus.ACCEPTED)
                    )
            ));

            // Event B: Recurring Weekly Engineering Standup (Every Mon, Wed, Fri)
            Instant nextMorning = now.plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(14, ChronoUnit.HOURS); // 10 AM EDT
            eventService.createEvent(demoUser.getId(), new EventDto.CreateEventRequest(
                    workCalendar.id(),
                    "Engineering Standup",
                    "Daily sync across platform, frontend, and MCP agents.",
                    "Slack Huddle",
                    workCalendar.color(),
                    false,
                    nextMorning,
                    nextMorning.plus(30, ChronoUnit.MINUTES),
                    "America/New_York",
                    "FREQ=WEEKLY;BYDAY=MO,WE,FR",
                    List.of(new EventDto.ReminderDto(null, 10, ReminderChannel.IN_APP))
            ));

            // Event C: Collaborative event on shared Project Alpha (Editable)
            Instant fridayRetro = now.plus(3, ChronoUnit.DAYS).plus(6, ChronoUnit.HOURS);
            eventService.createEvent(colleagueUser.getId(), new EventDto.CreateEventRequest(
                    alphaCalendar.id(),
                    "Project Alpha Milestone Review",
                    "Joint demo with Alex Rivera covering multi-calendar sharing features.",
                    "Room 402 / Zoom",
                    alphaCalendar.color(),
                    false,
                    fridayRetro,
                    fridayRetro.plus(45, ChronoUnit.MINUTES),
                    "America/Los_Angeles",
                    null,
                    List.of(new EventDto.ReminderDto(null, 30, ReminderChannel.IN_APP))
            ));

            // Event D: View-only event on Company Announcements
            Instant allHandsTime = now.plus(4, ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS);
            eventService.createEvent(colleagueUser.getId(), new EventDto.CreateEventRequest(
                    allHandsCalendar.id(),
                    "Quarterly All-Hands Meeting",
                    "Company quarterly roadmap and progress update.",
                    "Main Auditorium",
                    allHandsCalendar.color(),
                    false,
                    allHandsTime,
                    allHandsTime.plus(1, ChronoUnit.HOURS),
                    "America/New_York",
                    null,
                    List.of()
            ));

            log.info("Demo seeding completed successfully! Demo: demo@zoner.app / Password123!");
        } catch (Exception ex) {
            log.error("Failed to seed initial demo data: {}", ex.getMessage(), ex);
        }
    }
}
