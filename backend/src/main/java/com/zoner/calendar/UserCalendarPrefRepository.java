package com.zoner.calendar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCalendarPrefRepository extends JpaRepository<UserCalendarPref, Long> {

    @Query("SELECT p FROM UserCalendarPref p JOIN FETCH p.calendar WHERE p.user.id = :userId")
    List<UserCalendarPref> findAllByUserId(Long userId);

    @Query("SELECT p FROM UserCalendarPref p WHERE p.user.id = :userId AND p.calendar.id = :calendarId")
    Optional<UserCalendarPref> findByUserIdAndCalendarId(Long userId, Long calendarId);
}
