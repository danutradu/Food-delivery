package com.food.delivery.cart.repository;

import com.food.delivery.cart.model.CheckoutRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequestEntity, UUID> {
    Optional<CheckoutRequestEntity> findByCartId(UUID cartId);
}
