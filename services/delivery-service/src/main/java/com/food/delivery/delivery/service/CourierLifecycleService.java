package com.food.delivery.delivery.service;

import com.food.delivery.delivery.model.CourierEntity;
import com.food.delivery.delivery.model.CourierStatus;
import com.food.delivery.delivery.repository.CourierRepository;
import fd.user.CourierRoleGrantedV1;
import fd.user.CourierRoleRevokedV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourierLifecycleService {

    private final CourierRepository courierRepository;

    @Transactional
    public void grantCourierRole(CourierRoleGrantedV1 event) {
        var existing = courierRepository.findByUserId(event.getUserId());
        if (existing.isPresent()) {
            var courier = existing.get();
            if (event.getRoleVersion() <= courier.getRoleVersion()) {
                return;
            }
            if (courier.getStatus() == CourierStatus.SUSPENDED) {
                courier.setVehicle(event.getVehicleInformation());
                courier.setOperatingArea(event.getOperatingArea());
                courier.setStatus(CourierStatus.AVAILABLE);
            }
            courier.setRoleVersion(event.getRoleVersion());
            courierRepository.save(courier);
            return;
        }

        var courier = new CourierEntity();
        courier.setUserId(event.getUserId());
        courier.setVehicle(event.getVehicleInformation());
        courier.setOperatingArea(event.getOperatingArea());
        courier.setRoleVersion(event.getRoleVersion());
        courier.setStatus(CourierStatus.AVAILABLE);
        courierRepository.save(courier);
    }

    @Transactional
    public void revokeCourierRole(CourierRoleRevokedV1 event) {
        courierRepository.findByUserId(event.getUserId()).ifPresentOrElse(courier -> {
            if (event.getRoleVersion() <= courier.getRoleVersion()) {
                return;
            }
            courier.setStatus(CourierStatus.SUSPENDED);
            courier.setRoleVersion(event.getRoleVersion());
            courierRepository.save(courier);
        }, () -> {
            var courier = new CourierEntity();
            courier.setUserId(event.getUserId());
            courier.setStatus(CourierStatus.SUSPENDED);
            courier.setRoleVersion(event.getRoleVersion());
            courierRepository.save(courier);
        });
    }
}
