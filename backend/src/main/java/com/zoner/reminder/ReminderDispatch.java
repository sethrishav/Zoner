package com.zoner.reminder;

import com.zoner.event.Reminder;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "reminder_dispatches",
        uniqueConstraints = @UniqueConstraint(name = "uq_reminder_dispatches_reminder_occ", columnNames = {"reminder_id", "occurrence_start"})
)
public class ReminderDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false)
    private Reminder reminder;

    @Column(name = "occurrence_start", nullable = false)
    private Instant occurrenceStart;

    @Column(name = "fire_at", nullable = false)
    private Instant fireAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchStatus status = DispatchStatus.SENT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReminderDispatch() {}

    public ReminderDispatch(Reminder reminder, Instant occurrenceStart, Instant fireAt, DispatchStatus status) {
        this.reminder = reminder;
        this.occurrenceStart = occurrenceStart;
        this.fireAt = fireAt;
        this.status = (status != null) ? status : DispatchStatus.SENT;
        this.sentAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Reminder getReminder() {
        return reminder;
    }

    public void setReminder(Reminder reminder) {
        this.reminder = reminder;
    }

    public Instant getOccurrenceStart() {
        return occurrenceStart;
    }

    public void setOccurrenceStart(Instant occurrenceStart) {
        this.occurrenceStart = occurrenceStart;
    }

    public Instant getFireAt() {
        return fireAt;
    }

    public void setFireAt(Instant fireAt) {
        this.fireAt = fireAt;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchStatus status) {
        this.status = status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReminderDispatch that = (ReminderDispatch) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
