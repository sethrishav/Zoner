package com.zoner.reminder;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderDispatchRepository extends JpaRepository<ReminderDispatch, Long> {

    boolean existsByReminderIdAndOccurrenceStart(Long reminderId, Instant occurrenceStart);

    Optional<ReminderDispatch> findByReminderIdAndOccurrenceStart(Long reminderId, Instant occurrenceStart);
}
