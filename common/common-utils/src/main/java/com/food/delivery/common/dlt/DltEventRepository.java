package com.food.delivery.common.dlt;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DltEventRepository extends JpaRepository<DltEventEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DltEventEntity e where e.id = :id")
    Optional<DltEventEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select e from DltEventEntity e
            where (:status is null or e.status = :status)
              and (:serviceName is null or e.serviceName = :serviceName)
              and (:sourceTopic is null or e.sourceTopic = :sourceTopic)
            """)
    Page<DltEventEntity> findAllByFilters(DltEventStatus status, String serviceName,
                                          String sourceTopic, Pageable pageable);
}
