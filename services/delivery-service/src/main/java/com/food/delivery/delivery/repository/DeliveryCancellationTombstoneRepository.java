package com.food.delivery.delivery.repository;

import com.food.delivery.delivery.model.DeliveryCancellationTombstoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryCancellationTombstoneRepository extends JpaRepository<DeliveryCancellationTombstoneEntity, UUID> {
}
