package com.food.delivery.auth.service;

import com.food.delivery.auth.config.KafkaTopics;
import com.food.delivery.auth.dto.CourierApplicationRejectionRequest;
import com.food.delivery.auth.dto.CourierApplicationRequest;
import com.food.delivery.auth.dto.CourierApplicationResponse;
import com.food.delivery.auth.exception.CourierApplicationConflictException;
import com.food.delivery.auth.exception.CourierApplicationException;
import com.food.delivery.auth.exception.CourierApplicationNotFoundException;
import com.food.delivery.auth.model.CourierApplicationEntity;
import com.food.delivery.auth.model.CourierApplicationStatus;
import com.food.delivery.auth.repository.CourierApplicationRepository;
import com.food.delivery.auth.repository.RoleRepository;
import com.food.delivery.auth.repository.UserRepository;
import com.food.delivery.common.model.Role;
import com.food.delivery.common.outbox.OutboxService;
import fd.user.CourierRoleGrantedV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierApplicationService {

    private final UserRepository userRepository;
    private final CourierApplicationRepository applicationRepository;
    private final RoleRepository roleRepository;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    @Transactional
    public CourierApplicationResponse submit(UUID userId, CourierApplicationRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new CourierApplicationException("Authenticated user not found"));

        if (user.getRoles().stream().anyMatch(role -> role.getName().name().equals("COURIER"))) {
            throw new CourierApplicationConflictException("User is already a courier");
        }
        if (applicationRepository.existsByUserIdAndStatusIn(userId,
                Set.of(CourierApplicationStatus.PENDING, CourierApplicationStatus.APPROVED,
                        CourierApplicationStatus.PERMANENTLY_REJECTED))) {
            var message = applicationRepository.existsByUserIdAndStatus(userId, CourierApplicationStatus.PENDING)
                    ? "User already has a pending courier application"
                    : "User cannot submit another courier application";
            throw new CourierApplicationConflictException(message);
        }

        var application = new CourierApplicationEntity();
        application.setId(UUID.randomUUID());
        application.setUserId(userId);
        application.setFullName(request.fullName());
        application.setPhoneNumber(request.phoneNumber());
        application.setVehicleInformation(request.vehicleInformation());
        application.setOperatingArea(request.operatingArea());
        application.setStatus(CourierApplicationStatus.PENDING);
        application.setSubmittedAt(Instant.now());
        return CourierApplicationResponse.from(applicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public List<CourierApplicationResponse> findByStatus(CourierApplicationStatus status) {
        return applicationRepository.findByStatusOrderBySubmittedAtAsc(status).stream()
                .map(CourierApplicationResponse::from)
                .toList();
    }

    @Transactional
    public CourierApplicationResponse approve(UUID applicationId, UUID reviewerId) {
        var application = pendingApplication(applicationId);
        var user = userRepository.findByIdForUpdate(application.getUserId())
                .orElseThrow(() -> new CourierApplicationNotFoundException("Applicant user not found"));
        var courierRole = roleRepository.findByName(Role.COURIER)
                .orElseThrow(() -> new IllegalStateException("COURIER role not found in database"));

        user.getRoles().add(courierRole);
        var roleVersion = user.getCourierRoleVersion() + 1;
        user.setCourierRoleVersion(roleVersion);
        userRepository.save(user);

        application.setStatus(CourierApplicationStatus.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(reviewerId);
        var saved = applicationRepository.save(application);

        var event = new CourierRoleGrantedV1(
                UUID.randomUUID(),
                Instant.now(),
                saved.getId(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                saved.getFullName(),
                saved.getPhoneNumber(),
                saved.getVehicleInformation(),
                saved.getOperatingArea(),
                roleVersion);
        outboxService.publish(topics.getCourierRoleGranted(), user.getId().toString(), event);
        return CourierApplicationResponse.from(saved);
    }

    @Transactional
    public CourierApplicationResponse reject(UUID applicationId, UUID reviewerId,
                                             CourierApplicationRejectionRequest request) {
        var application = pendingApplication(applicationId);
        application.setStatus(request.permanent()
                ? CourierApplicationStatus.PERMANENTLY_REJECTED
                : CourierApplicationStatus.REJECTED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(reviewerId);
        application.setRejectionReason(request.reason());
        return CourierApplicationResponse.from(applicationRepository.save(application));
    }

    private CourierApplicationEntity pendingApplication(UUID applicationId) {
        var application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new CourierApplicationNotFoundException("Courier application not found"));
        if (application.getStatus() != CourierApplicationStatus.PENDING) {
            throw new CourierApplicationConflictException("Courier application is not pending");
        }
        return application;
    }
}
