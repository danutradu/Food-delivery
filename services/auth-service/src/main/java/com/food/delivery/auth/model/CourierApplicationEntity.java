package com.food.delivery.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_applications")
@Getter
@Setter
public class CourierApplicationEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 50)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String vehicleInformation;

    @Column(nullable = false, length = 150)
    private String operatingArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourierApplicationStatus status = CourierApplicationStatus.PENDING;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant reviewedAt;

    private UUID reviewedBy;

    @Column(length = 500)
    private String rejectionReason;
}
