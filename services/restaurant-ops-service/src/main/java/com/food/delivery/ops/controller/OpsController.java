package com.food.delivery.ops.controller;

import com.food.delivery.ops.dto.KitchenTicketResponse;
import com.food.delivery.ops.dto.StatusUpdateRequest;
import com.food.delivery.ops.model.KitchenTicketStatus;
import com.food.delivery.ops.service.OpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ops/orders")
@Slf4j
public class OpsController {

    private final OpsService opsService;

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
    @PatchMapping("/{orderId}/status")
    public KitchenTicketResponse updateStatus(@PathVariable UUID orderId, @RequestBody StatusUpdateRequest request) {
        return opsService.updateStatus(orderId, request);
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
    @GetMapping
    public Page<KitchenTicketResponse> getTickets(@RequestParam(required = false) KitchenTicketStatus status, @PageableDefault(size = 50) Pageable pageable) {
        return opsService.getTickets(status, pageable);
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
    @GetMapping("/{orderId}")
    public KitchenTicketResponse getTicket(@PathVariable UUID orderId) {
        return opsService.getTicket(orderId);
    }
}
