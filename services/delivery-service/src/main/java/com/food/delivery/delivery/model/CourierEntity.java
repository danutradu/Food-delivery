package com.food.delivery.delivery.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "couriers")
@Getter
@Setter
public class CourierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private String vehicle;

    @Column(length = 150)
    private String operatingArea;

    @Column(name = "role_version", nullable = false)
    private long roleVersion = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourierStatus status = CourierStatus.AVAILABLE;
}
