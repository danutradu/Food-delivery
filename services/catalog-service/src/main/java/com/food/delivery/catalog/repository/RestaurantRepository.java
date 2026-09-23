package com.food.delivery.catalog.repository;

import com.food.delivery.catalog.model.RestaurantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<RestaurantEntity, UUID> {}
