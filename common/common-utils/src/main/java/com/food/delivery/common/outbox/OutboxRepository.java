package com.food.delivery.common.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEventEntity> findByStatus(OutboxStatus status, Pageable pageable);

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = :status AND (e.claimedAt IS NULL OR e.claimedAt < :cutoff)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEventEntity> findStaleInFlightEvents(@Param("status") OutboxStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboxEventEntity> findByIdAndStatus(UUID id, OutboxStatus status);
}
