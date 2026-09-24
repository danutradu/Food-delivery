package com.food.delivery.delivery.service;

import com.food.delivery.delivery.model.CourierEntity;
import com.food.delivery.delivery.model.CourierStatus;
import com.food.delivery.delivery.repository.CourierRepository;
import fd.user.CourierRoleGrantedV1;
import fd.user.CourierRoleRevokedV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierLifecycleServiceTest {

    @Mock
    CourierRepository courierRepository;

    @InjectMocks
    CourierLifecycleService service;

    @Test
    void grantCourierRoleCreatesAvailableProfile() {
        var userId = UUID.randomUUID();
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.grantCourierRole(new CourierRoleGrantedV1(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), userId,
                "courier", "courier@example.com", "Courier", "+40123456789", "Bicycle", "Bucharest", 1L));

        var saved = ArgumentCaptor.forClass(CourierEntity.class);
        verify(courierRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getVehicle()).isEqualTo("Bicycle");
        assertThat(saved.getValue().getOperatingArea()).isEqualTo("Bucharest");
        assertThat(saved.getValue().getStatus()).isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void grantCourierRoleIsIdempotent() {
        var userId = UUID.randomUUID();
        var courier = new CourierEntity();
        courier.setRoleVersion(1L);
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));

        service.grantCourierRole(new CourierRoleGrantedV1(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), userId,
                "courier", "courier@example.com", "Courier", "+40123456789", "Bicycle", "Bucharest", 1L));

        verify(courierRepository, never()).save(any());
    }

    @Test
    void revokeCourierRoleSuspendsExistingProfile() {
        var userId = UUID.randomUUID();
        var courier = new CourierEntity();
        courier.setUserId(userId);
        courier.setStatus(CourierStatus.AVAILABLE);
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));

        service.revokeCourierRole(new CourierRoleRevokedV1(
                UUID.randomUUID(), Instant.now(), userId, "Admin disabled courier", 2L));

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.SUSPENDED);
        verify(courierRepository).save(courier);
    }

    @Test
    void revokeBeforeGrantCreatesSuspendedProfileAndStaleGrantCannotReactivateIt() {
        var userId = UUID.randomUUID();
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.empty(),
                Optional.of(suspendedCourier(userId, 2L)));

        service.revokeCourierRole(new CourierRoleRevokedV1(
                UUID.randomUUID(), Instant.now(), userId, "Admin disabled courier", 2L));
        service.grantCourierRole(new CourierRoleGrantedV1(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), userId,
                "courier", "courier@example.com", "Courier", "+40123456789", "Bicycle", "Bucharest", 1L));

        var saved = ArgumentCaptor.forClass(CourierEntity.class);
        verify(courierRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CourierStatus.SUSPENDED);
        assertThat(saved.getValue().getRoleVersion()).isEqualTo(2L);
    }

    private CourierEntity suspendedCourier(UUID userId, long roleVersion) {
        var courier = new CourierEntity();
        courier.setUserId(userId);
        courier.setStatus(CourierStatus.SUSPENDED);
        courier.setRoleVersion(roleVersion);
        return courier;
    }
}
