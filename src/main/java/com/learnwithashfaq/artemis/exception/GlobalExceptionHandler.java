package com.learnwithashfaq.artemis.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Catches all REST API exceptions in one place.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHY @RestControllerAdvice?
 * ═══════════════════════════════════════════════════════════════
 *
 * Without this, unhandled exceptions would return ugly 500 errors with
 * stack traces exposed to the client (security risk + bad UX).
 *
 * This class catches exceptions and returns clean, consistent JSON errors.
 *
 * NOTE: This handles REST API exceptions only.
 * JMS consumer exceptions are handled differently — they trigger
 * transaction rollback + retry, not HTTP error responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles OrderProcessingException and its subclasses.
     * Returns HTTP 500 with a descriptive error body.
     */
    @ExceptionHandler(OrderProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleOrderProcessingException(
            OrderProcessingException ex) {

        log.error("❌ Order processing error: {}", ex.getMessage());

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", "Order Processing Failed");
        errorBody.put("message", ex.getMessage());
        errorBody.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }

    /**
     * Handles IllegalArgumentException for bad input (e.g., negative quantity).
     * Returns HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {

        log.warn("⚠️ Bad request: {}", ex.getMessage());

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", "Bad Request");
        errorBody.put("message", ex.getMessage());
        errorBody.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    /**
     * Catch-all for any other unexpected exceptions.
     * Returns HTTP 500 with a generic message (don't expose internal details!).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        log.error("💥 Unexpected error: {}", ex.getMessage(), ex);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", "Internal Server Error");
        errorBody.put("message", "An unexpected error occurred. Please try again later.");
        errorBody.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }
}
