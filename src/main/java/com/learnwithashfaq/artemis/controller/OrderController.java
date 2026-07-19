package com.learnwithashfaq.artemis.controller;

import com.learnwithashfaq.artemis.dto.OrderRequest;
import com.learnwithashfaq.artemis.dto.OrderResponse;
import com.learnwithashfaq.artemis.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OrderController — REST endpoint for placing orders.
 *
 * ═══════════════════════════════════════════════════════════════
 * ENDPOINT:
 * ═══════════════════════════════════════════════════════════════
 *
 *   POST http://localhost:8080/api/orders
 *
 *   Request Body:
 *   {
 *       "productName": "iPhone 15 Pro",
 *       "quantity": 2,
 *       "price": 999.99,
 *       "customerName": "Ashfaq"
 *   }
 *
 *   Response (HTTP 202 ACCEPTED):
 *   {
 *       "message": "Order placed successfully! It will be processed in the background.",
 *       "orderId": "ORD-A1B2C3D4",
 *       "status": "ACCEPTED",
 *       "timestamp": "2024-01-15T10:30:00"
 *   }
 *
 * ═══════════════════════════════════════════════════════════════
 * WHY HTTP 202 ACCEPTED (not 200 OK or 201 CREATED)?
 * ═══════════════════════════════════════════════════════════════
 *
 * HTTP status codes have specific meanings:
 *
 *   200 OK      → "Here's your result" (synchronous, work is DONE)
 *   201 CREATED → "Resource was created" (synchronous, resource exists)
 *   202 ACCEPTED → "Got your request, working on it" (ASYNCHRONOUS!)
 *
 * Since we're sending the order to a JMS queue for BACKGROUND processing,
 * 202 ACCEPTED is the correct status code. It tells the client:
 * "Your order was received and will be processed — but it's not done yet."
 *
 * This is a REST API best practice for async operations!
 */
@RestController
@RequestMapping("/api/orders")
@Slf4j
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Places a new order.
     *
     * The order is sent to the JMS queue for async processing.
     * The user gets an immediate response with order ID.
     *
     * TEST SCENARIOS (from Postman):
     *
     * ✅ Happy Path:
     * {
     *     "productName": "iPhone 15 Pro",
     *     "quantity": 2,
     *     "price": 999.99,
     *     "customerName": "Ashfaq"
     * }
     *
     * ❌ Payment Failure (triggers retry + DLQ):
     * {
     *     "productName": "FAIL_PAYMENT",
     *     "quantity": 1,
     *     "price": 500.00,
     *     "customerName": "Ashfaq"
     * }
     *
     * ❌ Inventory Failure (triggers retry + DLQ):
     * {
     *     "productName": "FAIL_INVENTORY",
     *     "quantity": 1,
     *     "price": 300.00,
     *     "customerName": "Ashfaq"
     * }
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest orderRequest) {

        log.info("═══════════════════════════════════════════════════");
        log.info("🌐 REST API — Received order request");
        log.info("   Product  : {}", orderRequest.getProductName());
        log.info("   Quantity : {}", orderRequest.getQuantity());
        log.info("   Price    : ${}", orderRequest.getPrice());
        log.info("   Customer : {}", orderRequest.getCustomerName());
        log.info("═══════════════════════════════════════════════════");

        OrderResponse response = orderService.placeOrder(orderRequest);

        log.info("🌐 REST API — Returning HTTP 202 ACCEPTED with Order ID: {}",
                response.getOrderId());

        // HTTP 202 = "Accepted for processing" (async acknowledgment)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
