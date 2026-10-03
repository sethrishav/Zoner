package com.zoner.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventAttendeeRepository extends JpaRepository<EventAttendee, Long> {

    List<EventAttendee> findByEventId(Long eventId);

    Optional<EventAttendee> findByEventIdAndEmail(Long eventId, String email);

    void deleteByEventId(Long eventId);
}
