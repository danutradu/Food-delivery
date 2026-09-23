package com.food.delivery.ops.repository;

import com.food.delivery.ops.model.KitchenTicketEntity;
import com.food.delivery.ops.model.KitchenTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KitchenTicketRepository extends JpaRepository<KitchenTicketEntity, UUID> {
    Optional<KitchenTicketEntity> findByOrderId(UUID orderId);
    Page<KitchenTicketEntity> findByStatus(KitchenTicketStatus status, Pageable pageable);
}
