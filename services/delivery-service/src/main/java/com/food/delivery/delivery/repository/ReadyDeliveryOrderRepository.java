package com.food.delivery.delivery.repository;

import com.food.delivery.delivery.model.ReadyDeliveryOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReadyDeliveryOrderRepository extends JpaRepository<ReadyDeliveryOrderEntity, UUID> {
}
