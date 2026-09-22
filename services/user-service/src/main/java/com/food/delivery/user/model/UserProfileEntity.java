package com.food.delivery.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users_profile")
@Getter
@Setter
public class UserProfileEntity {

    @Id
    private UUID userId;

    private String username;

    private String email;
}
