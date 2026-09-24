package com.food.delivery.delivery.repository;

import com.food.delivery.delivery.model.CourierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<CourierEntity, UUID> {
    Optional<CourierEntity> findByUserId(UUID userId);

    @Query(value = "SELECT c.* FROM couriers c WHERE c.status = 'AVAILABLE' " +
            "AND NOT EXISTS (SELECT 1 FROM courier_assignments a WHERE a.delivery_id = :deliveryId AND a.courier_id = c.id) " +
            "ORDER BY c.id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<CourierEntity> findAvailableForUpdate(@Param("deliveryId") UUID deliveryId);
}
