package com.zoner.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    @Query("""
        SELECT r FROM Reminder r
        JOIN FETCH r.event e
        JOIN FETCH e.calendar c
        JOIN FETCH c.owner o
        JOIN FETCH e.createdBy cb
    """)
    List<Reminder> findAllWithEventAndRecipients();
}
