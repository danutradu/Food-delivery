package com.food.delivery.auth.repository;

import com.food.delivery.auth.model.RoleEntity;
import com.food.delivery.common.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(Role name);
}
