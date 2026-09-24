package com.food.delivery.auth.controller;

import com.food.delivery.auth.dto.CourierDisableRequest;
import com.food.delivery.auth.service.CourierAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/couriers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CourierAdministrationController {

    private final CourierAdministrationService administrationService;

    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disable(@PathVariable UUID userId,
                                        @Valid @RequestBody CourierDisableRequest request) {
        administrationService.disable(userId, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/enable")
    public ResponseEntity<Void> enable(@PathVariable UUID userId) {
        administrationService.enable(userId);
        return ResponseEntity.noContent().build();
    }
}
