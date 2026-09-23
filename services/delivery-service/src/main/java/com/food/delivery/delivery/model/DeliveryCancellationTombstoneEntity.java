package com.food.delivery.delivery.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cancelled_delivery_orders")
@Getter
@Setter
public class DeliveryCancellationTombstoneEntity {

    @Id
    private UUID orderId;

    private Instant cancelledAt;

    private String reason;
}
