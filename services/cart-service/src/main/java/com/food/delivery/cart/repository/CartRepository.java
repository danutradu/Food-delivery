package com.food.delivery.cart.repository;

import com.food.delivery.cart.model.CartEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {
    Optional<CartEntity> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartEntity c where c.customerId = :customerId")
    Optional<CartEntity> findByCustomerIdForCheckout(@Param("customerId") UUID customerId);
}
