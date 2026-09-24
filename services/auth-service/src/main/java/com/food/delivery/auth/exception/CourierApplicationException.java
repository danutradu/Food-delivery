package com.food.delivery.auth.exception;

public class CourierApplicationException extends RuntimeException {
    public CourierApplicationException(String message) {
        super(message);
    }
}
