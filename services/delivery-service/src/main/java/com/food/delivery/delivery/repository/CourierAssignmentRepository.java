package com.food.delivery.delivery.repository;

import com.food.delivery.delivery.model.AssignmentStatus;
import com.food.delivery.delivery.model.CourierAssignmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierAssignmentRepository extends JpaRepository<CourierAssignmentEntity, UUID>, JpaSpecificationExecutor<CourierAssignmentEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT assignment FROM CourierAssignmentEntity assignment WHERE assignment.id = :id")
    Optional<CourierAssignmentEntity> findForUpdateById(@Param("id") UUID id);

    List<CourierAssignmentEntity> findByStatusAndExpiresAtBefore(AssignmentStatus status, Instant cutoff);
}
