package com.food.delivery.common.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String payloadJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant updatedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    private Instant lastRetryAt;

    private Instant claimedAt;

    private String lastError;

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
