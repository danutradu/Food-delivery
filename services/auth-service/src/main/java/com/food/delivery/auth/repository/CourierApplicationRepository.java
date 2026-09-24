package com.food.delivery.auth.repository;

import com.food.delivery.auth.model.CourierApplicationEntity;
import com.food.delivery.auth.model.CourierApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierApplicationRepository extends JpaRepository<CourierApplicationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT application FROM CourierApplicationEntity application WHERE application.id = :id")
    Optional<CourierApplicationEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByUserIdAndStatus(UUID userId, CourierApplicationStatus status);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<CourierApplicationStatus> statuses);

    List<CourierApplicationEntity> findByStatusOrderBySubmittedAtAsc(CourierApplicationStatus status);

    Optional<CourierApplicationEntity> findFirstByUserIdAndStatusOrderByReviewedAtDesc(
            UUID userId, CourierApplicationStatus status);
}
