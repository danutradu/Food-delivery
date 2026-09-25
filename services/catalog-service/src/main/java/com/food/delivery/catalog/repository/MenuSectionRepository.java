package com.food.delivery.catalog.repository;

import com.food.delivery.catalog.model.MenuSectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuSectionRepository extends JpaRepository<MenuSectionEntity, UUID> {
    List<MenuSectionEntity> findByRestaurantIdOrderByDisplayOrderAscNameAsc(UUID restaurantId);
    boolean existsByRestaurantIdAndName(UUID restaurantId, String name);
    boolean existsByRestaurantIdAndNameAndIdNot(UUID restaurantId, String name, UUID id);
}
