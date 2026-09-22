package com.food.delivery.auth.util;

import com.food.delivery.auth.dto.RegisterRequest;
import com.food.delivery.auth.model.RoleEntity;
import com.food.delivery.auth.model.UserEntity;
import fd.user.UserRegisteredV1;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@UtilityClass
public class UserFactory {

    public UserEntity createUser(RegisterRequest req, Set<RoleEntity> roles) {
        var user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setEnabled(true);
        user.setRoles(roles);
        return user;
    }

    public UserRegisteredV1 createUserRegistered(UserEntity user) {
        var roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();

        return new UserRegisteredV1(
                UUID.randomUUID(),
                Instant.now(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roleNames);
    }
}
