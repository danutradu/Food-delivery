package com.food.delivery.catalog.exception;

import java.util.UUID;

public class MenuSectionNotFoundException extends RuntimeException {
    public MenuSectionNotFoundException(UUID sectionId) {
        super("Menu section not found: " + sectionId);
    }
}
