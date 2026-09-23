package com.food.delivery.delivery.repository;

import com.food.delivery.delivery.model.DeliveryEntity;
import com.food.delivery.delivery.model.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<DeliveryEntity, UUID> {
    Optional<DeliveryEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryEntity> findForUpdateById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryEntity> findForUpdateByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryEntity> findFirstByStatusOrderByCreatedAtAsc(DeliveryStatus status);
}
