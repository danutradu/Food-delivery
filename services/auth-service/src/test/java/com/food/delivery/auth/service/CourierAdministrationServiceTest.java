package com.food.delivery.auth.service;

import com.food.delivery.auth.config.KafkaTopics;
import com.food.delivery.auth.model.CourierApplicationEntity;
import com.food.delivery.auth.model.CourierApplicationStatus;
import com.food.delivery.auth.model.RoleEntity;
import com.food.delivery.auth.model.UserEntity;
import com.food.delivery.auth.repository.CourierApplicationRepository;
import com.food.delivery.auth.repository.RoleRepository;
import com.food.delivery.auth.repository.UserRepository;
import com.food.delivery.common.model.Role;
import com.food.delivery.common.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierAdministrationServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    CourierApplicationRepository applicationRepository;
    @Mock
    OutboxService outboxService;
    @Mock
    KafkaTopics topics;

    @InjectMocks
    CourierAdministrationService service;

    @Test
    void disableRemovesCourierRoleButRetainsCustomerRole() {
        var userId = UUID.randomUUID();
        var user = user(userId, Role.CUSTOMER, Role.COURIER);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(topics.getCourierRoleRevoked()).thenReturn("courier-role-revoked");

        service.disable(userId, "Policy violation");

        assertThat(user.getRoles()).extracting(RoleEntity::getName).containsExactlyInAnyOrder(Role.CUSTOMER);
        verify(userRepository).save(user);
        verify(outboxService).publish(eq("courier-role-revoked"), eq(userId.toString()), any());
    }

    @Test
    void enableAddsCourierRoleAndPublishesGrant() {
        var userId = UUID.randomUUID();
        var user = user(userId, Role.CUSTOMER);
        var courierRole = role(Role.COURIER);
        var application = new CourierApplicationEntity();
        application.setId(UUID.randomUUID());
        application.setUserId(userId);
        application.setStatus(CourierApplicationStatus.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setFullName("Jane Doe");
        application.setPhoneNumber("+40123456789");
        application.setVehicleInformation("Bicycle");
        application.setOperatingArea("Bucharest");
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(applicationRepository.findFirstByUserIdAndStatusOrderByReviewedAtDesc(userId, CourierApplicationStatus.APPROVED))
                .thenReturn(Optional.of(application));
        when(roleRepository.findByName(Role.COURIER)).thenReturn(Optional.of(courierRole));
        when(topics.getCourierRoleGranted()).thenReturn("courier-role-granted");

        service.enable(userId);

        assertThat(user.getRoles()).extracting(RoleEntity::getName).containsExactlyInAnyOrder(Role.CUSTOMER, Role.COURIER);
        verify(userRepository).save(user);
        verify(outboxService).publish(eq("courier-role-granted"), eq(userId.toString()), any());
    }

    private UserEntity user(UUID userId, Role... roles) {
        var user = new UserEntity();
        user.setId(userId);
        user.setUsername("jane");
        user.setEmail("jane@example.com");
        user.setRoles(new HashSet<>());
        for (var role : roles) user.getRoles().add(role(role));
        return user;
    }

    private RoleEntity role(Role role) {
        var entity = new RoleEntity();
        entity.setName(role);
        return entity;
    }
}
