package com.food.delivery.ops.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_tickets")
@Getter
@Setter
public class KitchenTicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KitchenTicketStatus status = KitchenTicketStatus.PENDING;

    private String specialInstructions;

    private int estimatedPrepTimeMinutes;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column
    private Instant updatedAt;

    private Instant acceptedAt;

    private Instant startedAt;

    private Instant readyAt;

    @PrePersist
    public void prePersist() {
        updatedAt = receivedAt != null ? receivedAt : Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
