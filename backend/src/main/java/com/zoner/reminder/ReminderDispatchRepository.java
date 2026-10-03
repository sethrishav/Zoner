package com.zoner.reminder;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ReminderDispatchRepository extends JpaRepository<ReminderDispatch, Long> {

    boolean existsByReminderIdAndOccurrenceStart(Long reminderId, Instant occurrenceStart);

    Optional<ReminderDispatch> findByReminderIdAndOccurrenceStart(Long reminderId, Instant occurrenceStart);

    @Modifying
    @Transactional
    @Query("DELETE FROM ReminderDispatch d WHERE d.reminder.id = :reminderId AND d.occurrenceStart = :occurrenceStart")
    void deleteByReminderIdAndOccurrenceStart(@Param("reminderId") Long reminderId, @Param("occurrenceStart") Instant occurrenceStart);

    @Modifying
    @Transactional
    @Query("DELETE FROM ReminderDispatch d WHERE d.reminder.id = :reminderId")
    void deleteByReminderId(@Param("reminderId") Long reminderId);
}
