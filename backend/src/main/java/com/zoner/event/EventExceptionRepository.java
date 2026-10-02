package com.zoner.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventExceptionRepository extends JpaRepository<EventException, Long> {

    @Query("SELECT ex FROM EventException ex WHERE ex.event.id = :eventId")
    List<EventException> findAllByEventId(@Param("eventId") Long eventId);

    @Query("SELECT ex FROM EventException ex WHERE ex.event.id IN :eventIds")
    List<EventException> findAllByEventIdIn(@Param("eventIds") List<Long> eventIds);

    @Query("SELECT ex FROM EventException ex WHERE ex.event.id = :eventId AND ex.originalStart = :originalStart")
    Optional<EventException> findByEventIdAndOriginalStart(
            @Param("eventId") Long eventId,
            @Param("originalStart") Instant originalStart
    );

    void deleteAllByEventId(Long eventId);
}
