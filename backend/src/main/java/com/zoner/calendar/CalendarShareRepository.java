package com.zoner.calendar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarShareRepository extends JpaRepository<CalendarShare, Long> {

    @Query("SELECT cs FROM CalendarShare cs JOIN FETCH cs.calendar c JOIN FETCH c.owner JOIN FETCH cs.user WHERE cs.user.id = :userId")
    List<CalendarShare> findAllByUserIdWithCalendar(Long userId);

    @Query("SELECT cs FROM CalendarShare cs JOIN FETCH cs.user WHERE cs.calendar.id = :calendarId ORDER BY cs.user.displayName ASC")
    List<CalendarShare> findAllByCalendarIdWithUser(Long calendarId);

    @Query("SELECT cs FROM CalendarShare cs WHERE cs.calendar.id = :calendarId AND cs.user.id = :userId")
    Optional<CalendarShare> findByCalendarIdAndUserId(Long calendarId, Long userId);

    @Query("SELECT cs FROM CalendarShare cs JOIN FETCH cs.calendar WHERE cs.calendar.id = :calendarId AND cs.user.id = :userId")
    Optional<CalendarShare> findByCalendarIdAndUserIdWithCalendar(Long calendarId, Long userId);

    void deleteByCalendarIdAndUserId(Long calendarId, Long userId);
}
