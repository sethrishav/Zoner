package com.zoner.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e JOIN FETCH e.calendar LEFT JOIN FETCH e.reminders WHERE e.id = :id")
    Optional<Event> findByIdWithCalendarAndReminders(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.startAt < :to
          AND e.endAt > :from
        ORDER BY e.startAt ASC
    """)
    List<Event> findEventsInRange(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.recurrenceRule IS NULL
          AND e.startAt < :to
          AND e.endAt > :from
        ORDER BY e.startAt ASC
    """)
    List<Event> findNonRecurringEventsInRange(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.recurrenceRule IS NOT NULL
          AND e.startAt < :to
          AND (e.recurrenceUntil IS NULL OR e.recurrenceUntil > :from)
        ORDER BY e.startAt ASC
    """)
    List<Event> findRecurringEventsCandidates(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND (
               LOWER(e.title) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.location) LIKE LOWER(CONCAT('%', :term, '%'))
          )
        ORDER BY e.startAt ASC
    """)
    List<Event> searchEvents(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("term") String term,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.endAt > :from
          AND e.startAt < :to
          AND (
               LOWER(e.title) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.location) LIKE LOWER(CONCAT('%', :term, '%'))
          )
        ORDER BY e.startAt ASC
    """)
    List<Event> searchEventsWithRange(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("term") String term,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.endAt > :from
          AND (
               LOWER(e.title) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.location) LIKE LOWER(CONCAT('%', :term, '%'))
          )
        ORDER BY e.startAt ASC
    """)
    List<Event> searchEventsFrom(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("term") String term,
            @Param("from") Instant from,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.startAt < :to
          AND (
               LOWER(e.title) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(e.location) LIKE LOWER(CONCAT('%', :term, '%'))
          )
        ORDER BY e.startAt ASC
    """)
    List<Event> searchEventsTo(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("term") String term,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.startAt < :to
          AND e.endAt > :from
        ORDER BY e.startAt ASC
    """)
    List<Event> findConflictingEvents(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
        SELECT DISTINCT e FROM Event e
        JOIN FETCH e.calendar
        LEFT JOIN FETCH e.reminders
        WHERE e.calendar.id IN :calendarIds
          AND e.id <> :excludeEventId
          AND e.startAt < :to
          AND e.endAt > :from
        ORDER BY e.startAt ASC
    """)
    List<Event> findConflictingEventsExcluding(
            @Param("calendarIds") List<Long> calendarIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeEventId") Long excludeEventId
    );
}
