package com.food.delivery.cart.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({CartNotFoundForUserException.class, CartItemNotFoundException.class, MenuItemNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException e) {
        log.warn(e.getMessage());
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler({RestaurantMismatchException.class, EmptyCartException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException e) {
        log.warn("Invalid request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("Missing request header: {}", e.getHeaderName());
        return problem(HttpStatus.BAD_REQUEST, "MISSING_HEADER", e.getHeaderName() + " header is required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        var detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
