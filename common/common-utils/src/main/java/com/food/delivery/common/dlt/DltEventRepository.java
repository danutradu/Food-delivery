package com.food.delivery.common.dlt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DltEventRepository extends JpaRepository<DltEventEntity, UUID> {
}
