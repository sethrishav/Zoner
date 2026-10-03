package com.zoner.event;

import com.zoner.auth.User;
import com.zoner.calendar.Calendar;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @Column(length = 30)
    private String color;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = false;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "start_local", nullable = false)
    private LocalDateTime startLocal;

    @Column(name = "end_local", nullable = false)
    private LocalDateTime endLocal;

    @Column(name = "time_zone", nullable = false, length = 100)
    private String timeZone;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Column(name = "recurrence_until")
    private Instant recurrenceUntil;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventAttendee> attendees = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Event() {}

    public Event(Calendar calendar, String title, String description, String location, String color,
                 boolean allDay, Instant startAt, Instant endAt, String timeZone, User createdBy) {
        this.calendar = calendar;
        this.title = title;
        this.description = description;
        this.location = location;
        this.color = color;
        this.allDay = allDay;
        this.startAt = startAt;
        this.endAt = endAt;
        this.timeZone = (timeZone != null && !timeZone.isBlank()) ? timeZone : "UTC";
        this.createdBy = createdBy;
        recalculateLocalTimes();
    }

    public void recalculateLocalTimes() {
        if (this.timeZone != null && this.startAt != null && this.endAt != null) {
            ZoneId zoneId = ZoneId.of(this.timeZone);
            this.startLocal = LocalDateTime.ofInstant(this.startAt, zoneId);
            this.endLocal = LocalDateTime.ofInstant(this.endAt, zoneId);
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        recalculateLocalTimes();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        recalculateLocalTimes();
    }

    public void addReminder(int minutesBefore, ReminderChannel channel) {
        Reminder reminder = new Reminder(this, minutesBefore, channel);
        this.reminders.add(reminder);
    }

    public void clearReminders() {
        this.reminders.clear();
    }

    public void addAttendee(String email, String displayName, AttendeeStatus status) {
        EventAttendee attendee = new EventAttendee(this, email, displayName, status);
        this.attendees.add(attendee);
    }

    public void clearAttendees() {
        this.attendees.clear();
    }

    public List<EventAttendee> getAttendees() {
        return attendees;
    }

    public void setAttendees(List<EventAttendee> attendees) {
        this.attendees = attendees;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public LocalDateTime getStartLocal() {
        return startLocal;
    }

    public void setStartLocal(LocalDateTime startLocal) {
        this.startLocal = startLocal;
    }

    public LocalDateTime getEndLocal() {
        return endLocal;
    }

    public void setEndLocal(LocalDateTime endLocal) {
        this.endLocal = endLocal;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public void setRecurrenceRule(String recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
    }

    public Instant getRecurrenceUntil() {
        return recurrenceUntil;
    }

    public void setRecurrenceUntil(Instant recurrenceUntil) {
        this.recurrenceUntil = recurrenceUntil;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
