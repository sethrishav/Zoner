package com.zoner.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "event_exceptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_event_exceptions_event_orig_start", columnNames = {"event_id", "original_start"})
)
public class EventException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "original_start", nullable = false)
    private Instant originalStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 20)
    private ExceptionType exceptionType;

    @Column(name = "override_title", length = 255)
    private String overrideTitle;

    @Column(name = "override_desc", columnDefinition = "TEXT")
    private String overrideDesc;

    @Column(name = "override_location", length = 255)
    private String overrideLocation;

    @Column(name = "override_color", length = 30)
    private String overrideColor;

    @Column(name = "override_start_at")
    private Instant overrideStartAt;

    @Column(name = "override_end_at")
    private Instant overrideEndAt;

    @Column(name = "override_all_day")
    private Boolean overrideAllDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EventException() {}

    public EventException(Event event, Instant originalStart, ExceptionType exceptionType) {
        this.event = event;
        this.originalStart = originalStart;
        this.exceptionType = exceptionType;
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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public Instant getOriginalStart() {
        return originalStart;
    }

    public void setOriginalStart(Instant originalStart) {
        this.originalStart = originalStart;
    }

    public ExceptionType getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(ExceptionType exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getOverrideTitle() {
        return overrideTitle;
    }

    public void setOverrideTitle(String overrideTitle) {
        this.overrideTitle = overrideTitle;
    }

    public String getOverrideDesc() {
        return overrideDesc;
    }

    public void setOverrideDesc(String overrideDesc) {
        this.overrideDesc = overrideDesc;
    }

    public String getOverrideLocation() {
        return overrideLocation;
    }

    public void setOverrideLocation(String overrideLocation) {
        this.overrideLocation = overrideLocation;
    }

    public String getOverrideColor() {
        return overrideColor;
    }

    public void setOverrideColor(String overrideColor) {
        this.overrideColor = overrideColor;
    }

    public Instant getOverrideStartAt() {
        return overrideStartAt;
    }

    public void setOverrideStartAt(Instant overrideStartAt) {
        this.overrideStartAt = overrideStartAt;
    }

    public Instant getOverrideEndAt() {
        return overrideEndAt;
    }

    public void setOverrideEndAt(Instant overrideEndAt) {
        this.overrideEndAt = overrideEndAt;
    }

    public Boolean getOverrideAllDay() {
        return overrideAllDay;
    }

    public void setOverrideAllDay(Boolean overrideAllDay) {
        this.overrideAllDay = overrideAllDay;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventException that = (EventException) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
