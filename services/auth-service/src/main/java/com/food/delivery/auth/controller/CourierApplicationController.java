package com.food.delivery.auth.controller;

import com.food.delivery.auth.dto.CourierApplicationRejectionRequest;
import com.food.delivery.auth.dto.CourierApplicationRequest;
import com.food.delivery.auth.dto.CourierApplicationResponse;
import com.food.delivery.auth.model.CourierApplicationStatus;
import com.food.delivery.auth.service.CourierApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courier-applications")
@RequiredArgsConstructor
public class CourierApplicationController {

    private final CourierApplicationService applicationService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CourierApplicationResponse> submit(
            @Valid @RequestBody CourierApplicationRequest request,
            Authentication authentication) {
        var userId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.submit(userId, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CourierApplicationResponse>> list(
            @RequestParam(defaultValue = "PENDING") CourierApplicationStatus status) {
        return ResponseEntity.ok(applicationService.findByStatus(status));
    }

    @PostMapping("/{applicationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourierApplicationResponse> approve(
            @PathVariable UUID applicationId,
            Authentication authentication) {
        return ResponseEntity.ok(applicationService.approve(applicationId, UUID.fromString(authentication.getName())));
    }

    @PostMapping("/{applicationId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourierApplicationResponse> reject(
            @PathVariable UUID applicationId,
            @Valid @RequestBody CourierApplicationRejectionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(applicationService.reject(applicationId, UUID.fromString(authentication.getName()), request));
    }
}
