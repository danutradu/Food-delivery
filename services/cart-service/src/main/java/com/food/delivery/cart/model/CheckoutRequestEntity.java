package com.food.delivery.cart.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "checkout_requests")
@Getter
@Setter
public class CheckoutRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "cart_id", nullable = false, unique = true)
    private UUID cartId;
}
