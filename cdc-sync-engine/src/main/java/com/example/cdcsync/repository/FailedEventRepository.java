package com.example.cdcsync.repository;

import com.example.cdcsync.model.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
    Optional<FailedEvent> findByEventId(String eventId);
}
