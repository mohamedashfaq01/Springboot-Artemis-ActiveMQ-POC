package com.learnwithashfaq.artemis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OrderResponse — What the REST API returns to the user after placing an order.
 *
 * ═══════════════════════════════════════════════════════════════
 * IMPORTANT CONCEPT: ASYNC ACKNOWLEDGMENT
 * ═══════════════════════════════════════════════════════════════
 *
 * Notice that the response says "ACCEPTED" — NOT "COMPLETED".
 *
 * Why? Because the order is being processed ASYNCHRONOUSLY:
 *
 *   User places order → REST API says "Got it!" (ACCEPTED)
 *                     → Message goes to queue
 *                     → Consumer processes it in the background
 *                     → User doesn't wait for processing to finish
 *
 * This is the fundamental difference between REST (sync) and JMS (async):
 *   - REST:  User waits for the full result
 *   - JMS:   User gets immediate acknowledgment, processing happens later
 *
 * In a real app, the user would check order status via:
 *   GET /api/orders/{orderId}   → Returns current status
 *
 * ═══════════════════════════════════════════════════════════════
 * EXAMPLE RESPONSE:
 * ═══════════════════════════════════════════════════════════════
 *
 * {
 *     "message": "Order placed successfully! Processing in background.",
 *     "orderId": "ORD-a1b2c3d4",
 *     "status": "ACCEPTED",
 *     "timestamp": "2024-01-15T10:30:00"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    /** Human-readable message for the API consumer. */
    private String message;

    /** The generated order ID for tracking. */
    private String orderId;

    /** Status: always "ACCEPTED" at this point (processing hasn't started yet). */
    private String status;

    /** When the order was accepted. */
    private LocalDateTime timestamp;
}
