package com.food.delivery.auth.service;

import com.food.delivery.auth.config.KafkaTopics;
import com.food.delivery.auth.dto.CourierApplicationRejectionRequest;
import com.food.delivery.auth.dto.CourierApplicationRequest;
import com.food.delivery.auth.model.CourierApplicationEntity;
import com.food.delivery.auth.model.CourierApplicationStatus;
import com.food.delivery.auth.model.RoleEntity;
import com.food.delivery.auth.model.UserEntity;
import com.food.delivery.auth.repository.CourierApplicationRepository;
import com.food.delivery.auth.repository.RoleRepository;
import com.food.delivery.auth.repository.UserRepository;
import com.food.delivery.common.model.Role;
import com.food.delivery.common.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierApplicationServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    CourierApplicationRepository applicationRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    OutboxService outboxService;
    @Mock
    KafkaTopics topics;

    @InjectMocks
    CourierApplicationService service;

    private UUID userId;
    private UserEntity user;
    private RoleEntity customerRole;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserEntity();
        user.setId(userId);
        user.setRoles(new HashSet<>());
        customerRole = new RoleEntity();
        customerRole.setName(Role.CUSTOMER);
        user.getRoles().add(customerRole);
    }

    @Test
    void submitCreatesPendingApplicationForCustomer() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(userId,
                new CourierApplicationRequest("Jane Doe", "+40123456789", "Bicycle", "Bucharest"));

        assertThat(response.status()).isEqualTo(CourierApplicationStatus.PENDING);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.operatingArea()).isEqualTo("Bucharest");
        verify(applicationRepository).save(any(CourierApplicationEntity.class));
    }

    @Test
    void submitRejectsSecondPendingApplication() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(applicationRepository.existsByUserIdAndStatusIn(eq(userId), anySet())).thenReturn(true);
        when(applicationRepository.existsByUserIdAndStatus(userId, CourierApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(userId,
                new CourierApplicationRequest("Jane Doe", "+40123456789", "Bicycle", "Bucharest")))
                .hasMessageContaining("pending courier application");
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void approveAddsCourierRoleAndPublishesEvent() {
        var application = application(CourierApplicationStatus.PENDING);
        var courierRole = new RoleEntity();
        courierRole.setName(Role.COURIER);
        when(applicationRepository.findByIdForUpdate(application.getId())).thenReturn(Optional.of(application));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(Role.COURIER)).thenReturn(Optional.of(courierRole));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(topics.getCourierRoleGranted()).thenReturn("courier-role-granted");

        var result = service.approve(application.getId(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(CourierApplicationStatus.APPROVED);
        assertThat(user.getRoles()).extracting(RoleEntity::getName).contains(Role.CUSTOMER, Role.COURIER);
        verify(outboxService).publish(eq("courier-role-granted"), eq(userId.toString()), any());
    }

    @Test
    void rejectRecordsReasonAndAllowsNormalReapplication() {
        var application = application(CourierApplicationStatus.PENDING);
        when(applicationRepository.findByIdForUpdate(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.reject(application.getId(), UUID.randomUUID(),
                new CourierApplicationRejectionRequest("Vehicle information is incomplete", false));

        assertThat(result.status()).isEqualTo(CourierApplicationStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo("Vehicle information is incomplete");
    }

    private CourierApplicationEntity application(CourierApplicationStatus status) {
        var application = new CourierApplicationEntity();
        application.setId(UUID.randomUUID());
        application.setUserId(userId);
        application.setFullName("Jane Doe");
        application.setPhoneNumber("+40123456789");
        application.setVehicleInformation("Bicycle");
        application.setOperatingArea("Bucharest");
        application.setStatus(status);
        application.setSubmittedAt(Instant.now());
        return application;
    }
}
