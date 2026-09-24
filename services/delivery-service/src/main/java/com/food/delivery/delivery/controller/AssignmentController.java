package com.food.delivery.delivery.controller;

import com.food.delivery.delivery.dto.AssignmentResponse;
import com.food.delivery.delivery.model.AssignmentStatus;
import com.food.delivery.delivery.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/assignments")
@Slf4j
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PreAuthorize("hasAnyRole('COURIER','ADMIN')")
    @GetMapping
    public List<AssignmentResponse> getAssignments(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID courierId,
            Authentication auth) {
        return assignmentService.getAssignments(active, status, orderId, courierId, getUserId(auth), isAdmin(auth));
    }

    @PreAuthorize("hasAnyRole('COURIER','ADMIN')")
    @GetMapping("/{assignmentId}")
    public AssignmentResponse getAssignment(@PathVariable UUID assignmentId, Authentication auth) {
        return assignmentService.getAssignment(assignmentId, getUserId(auth), isAdmin(auth));
    }

    @PreAuthorize("hasRole('COURIER')")
    @PostMapping("/{assignmentId}/accept")
    public void accept(@PathVariable UUID assignmentId, Authentication auth) {
        assignmentService.acceptAssignment(assignmentId, getUserId(auth));
    }

    @PreAuthorize("hasRole('COURIER')")
    @PostMapping("/{assignmentId}/reject")
    public void reject(@PathVariable UUID assignmentId, Authentication auth) {
        assignmentService.rejectAssignment(assignmentId, getUserId(auth));
    }

    @PreAuthorize("hasRole('COURIER')")
    @PostMapping("/{assignmentId}/pickup")
    public void pickup(@PathVariable UUID assignmentId, Authentication auth) {
        assignmentService.markAsPickedUp(assignmentId, getUserId(auth));
    }

    @PreAuthorize("hasRole('COURIER')")
    @PostMapping("/{assignmentId}/deliver")
    public void deliver(@PathVariable UUID assignmentId, Authentication auth) {
        assignmentService.markAsDelivered(assignmentId, getUserId(auth));
    }

    private UUID getUserId(Authentication auth) {
        return UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
