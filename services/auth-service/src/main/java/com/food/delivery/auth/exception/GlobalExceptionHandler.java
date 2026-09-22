package com.food.delivery.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException e) {
        log.warn(e.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ProblemDetail> handleAccountDisabledException(AccountDisabledException e) {
        log.warn(e.getMessage());
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", e.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleUserAlreadyExistsException(UserAlreadyExistsException e) {
        log.warn(e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "USER_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception e) {
        var detail = e instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "))
                : e.getMessage();
        log.warn("Invalid request: {}", detail);
        return problem(HttpStatus.BAD_REQUEST, e instanceof MethodArgumentNotValidException ? "VALIDATION_ERROR" : "INVALID_ARGUMENT", detail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
