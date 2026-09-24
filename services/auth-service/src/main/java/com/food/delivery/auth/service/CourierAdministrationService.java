package com.food.delivery.auth.service;

import com.food.delivery.auth.config.KafkaTopics;
import com.food.delivery.auth.exception.CourierApplicationConflictException;
import com.food.delivery.auth.exception.CourierApplicationNotFoundException;
import com.food.delivery.auth.model.CourierApplicationStatus;
import com.food.delivery.auth.repository.CourierApplicationRepository;
import com.food.delivery.auth.repository.RoleRepository;
import com.food.delivery.auth.repository.UserRepository;
import com.food.delivery.common.model.Role;
import com.food.delivery.common.outbox.OutboxService;
import fd.user.CourierRoleGrantedV1;
import fd.user.CourierRoleRevokedV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierAdministrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CourierApplicationRepository applicationRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    @Transactional
    public void disable(UUID userId, String reason) {
        var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CourierApplicationNotFoundException("User not found"));
        var removed = user.getRoles().removeIf(role -> role.getName() == Role.COURIER);
        if (!removed) {
            throw new CourierApplicationConflictException("User is not an active courier");
        }
        var roleVersion = user.getCourierRoleVersion() + 1;
        user.setCourierRoleVersion(roleVersion);
        userRepository.save(user);
        outboxService.publish(topics.getCourierRoleRevoked(), userId.toString(),
                new CourierRoleRevokedV1(UUID.randomUUID(), Instant.now(), userId, reason, roleVersion));
    }

    @Transactional
    public void enable(UUID userId) {
        var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CourierApplicationNotFoundException("User not found"));
        var application = applicationRepository.findFirstByUserIdAndStatusOrderByReviewedAtDesc(
                        userId, CourierApplicationStatus.APPROVED)
                .orElseThrow(() -> new CourierApplicationConflictException("No approved courier application found"));
        var courierRole = roleRepository.findByName(Role.COURIER)
                .orElseThrow(() -> new IllegalStateException("COURIER role not found in database"));
        if (user.getRoles().add(courierRole)) {
            var roleVersion = user.getCourierRoleVersion() + 1;
            user.setCourierRoleVersion(roleVersion);
            userRepository.save(user);
            outboxService.publish(topics.getCourierRoleGranted(), userId.toString(),
                    new CourierRoleGrantedV1(UUID.randomUUID(), Instant.now(), application.getId(),
                            user.getId(), user.getUsername(), user.getEmail(), application.getFullName(),
                            application.getPhoneNumber(), application.getVehicleInformation(), application.getOperatingArea(), roleVersion));
        }
    }
}
