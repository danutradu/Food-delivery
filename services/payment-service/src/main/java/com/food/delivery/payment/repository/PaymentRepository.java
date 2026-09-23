package com.food.delivery.payment.repository;

import com.food.delivery.payment.model.PaymentEntity;
import com.food.delivery.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByOrderId(UUID orderId);
    Optional<PaymentEntity> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
