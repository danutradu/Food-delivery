package com.food.delivery.catalog.repository;

import com.food.delivery.catalog.model.MenuItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {
    Page<MenuItemEntity> findByRestaurantId(UUID restaurantId, Pageable pageable);
}
