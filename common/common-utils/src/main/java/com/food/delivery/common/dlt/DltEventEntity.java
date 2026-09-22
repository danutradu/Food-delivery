package com.food.delivery.common.dlt;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlt_events")
@Getter
@Setter
public class DltEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String sourceTopic;

    @Column(nullable = false)
    private String dltTopic;

    private String originalKey;

    private Integer originalPartition;

    private Long originalOffset;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private byte[] payloadBytes;

    @Column(nullable = false)
    private String payloadEncoding;

    @Column(nullable = false)
    private String schemaFullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DltEventStatus status;

    @Column(nullable = false)
    private Instant parkedAt;
}
