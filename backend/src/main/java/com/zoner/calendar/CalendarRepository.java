package com.zoner.calendar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    @Query("SELECT c FROM Calendar c JOIN FETCH c.owner WHERE c.owner.id = :ownerId ORDER BY c.isDefault DESC, c.name ASC")
    List<Calendar> findAllByOwnerId(Long ownerId);

    @Query("SELECT c FROM Calendar c JOIN FETCH c.owner WHERE c.id = :id")
    Optional<Calendar> findByIdWithOwner(Long id);

    @Query("SELECT c FROM Calendar c WHERE c.owner.id = :ownerId AND c.isDefault = true")
    Optional<Calendar> findDefaultByOwnerId(Long ownerId);

    @Query("SELECT count(c) > 0 FROM Calendar c WHERE c.owner.id = :ownerId AND lower(c.name) = lower(:name)")
    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
}
