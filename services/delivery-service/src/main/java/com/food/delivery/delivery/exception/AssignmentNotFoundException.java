package com.food.delivery.delivery.exception;

import java.util.UUID;

public class AssignmentNotFoundException extends RuntimeException {
    public AssignmentNotFoundException(UUID id) {
        super("Assignment not found: " + id);
    }
}
