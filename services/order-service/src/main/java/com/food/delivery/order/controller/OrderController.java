package com.food.delivery.order.controller;

import com.food.delivery.order.dto.OrderResponse;
import com.food.delivery.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId, Authentication auth) {
        var userId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        return orderService.getOrder(orderId, userId);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders/by-cart/{cartId}")
    public OrderResponse getOrderByCart(@PathVariable UUID cartId, Authentication auth) {
        var userId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        return orderService.getOrderByCartId(cartId, userId);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders")
    public Page<OrderResponse> getOrders(Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        var userId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        return orderService.getOrdersByCustomer(userId, pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/orders")
    public Page<OrderResponse> getAllOrders(@PageableDefault(size = 50) Pageable pageable) {
        return orderService.getAllOrders(pageable);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PatchMapping("/orders/{orderId}/cancellation")
    public void cancel(@PathVariable UUID orderId, @RequestParam(defaultValue = "Customer requested") String reason, Authentication auth) {
        var userId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        orderService.cancelOrder(orderId, userId, reason);
    }
}
