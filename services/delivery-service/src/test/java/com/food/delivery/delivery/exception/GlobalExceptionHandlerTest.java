package com.food.delivery.delivery.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceSpecificNotFoundExceptionsReturnNotFound() {
        var deliveryResponse = handler.handleResourceNotFoundException(new DeliveryNotFoundException(UUID.randomUUID()));
        var courierResponse = handler.handleResourceNotFoundException(new CourierNotFoundException(UUID.randomUUID()));

        assertThat(deliveryResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(courierResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignmentStateConflictReturnsConflict() {
        var response = handler.handleAssignmentStateConflictException(
                new AssignmentStateConflictException("Invalid transition"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "ASSIGNMENT_STATE_CONFLICT");
    }
}
