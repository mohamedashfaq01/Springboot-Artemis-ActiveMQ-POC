package com.learnwithashfaq.artemis.service;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.dto.OrderRequest;
import com.learnwithashfaq.artemis.dto.OrderResponse;
import com.learnwithashfaq.artemis.producer.OrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OrderService — The orchestrator between REST API and JMS.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHAT DOES THIS CLASS DO?
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. Receives the OrderRequest from the REST Controller
 * 2. Validates the input
 * 3. Enriches it (generates orderId, calculates total, sets timestamp)
 * 4. Converts it to an OrderEvent (JMS message DTO)
 * 5. Sends it to the queue via OrderProducer
 * 6. Returns an OrderResponse to the user (ACCEPTED — not COMPLETED!)
 *
 * ═══════════════════════════════════════════════════════════════
 * WHY SEPARATE Service FROM Controller?
 * ═══════════════════════════════════════════════════════════════
 *
 * Single Responsibility Principle:
 *   - Controller → handles HTTP concerns (request/response, status codes)
 *   - Service    → handles business logic (validation, enrichment, orchestration)
 *   - Producer   → handles JMS concerns (sending messages)
 *
 * This makes each class easy to test and modify independently.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderProducer orderProducer;

    /**
     * Places an order by sending it to the JMS queue for async processing.
     *
     * @param orderRequest The REST API request from the user
     * @return OrderResponse with order ID and ACCEPTED status
     */
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        log.info("═══════════════════════════════════════════════════");
        log.info("📋 ORDER SERVICE — Processing new order request");
        log.info("═══════════════════════════════════════════════════");

        // ─── Step 1: Validate input ───
        validateOrderRequest(orderRequest);

        // ─── Step 2: Generate unique order ID ───
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("🆔 Generated Order ID: {}", orderId);

        // ─── Step 3: Build the JMS message (OrderEvent) ───
        // This is the enriched version of the request with system-generated fields
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId(orderId)
                .productName(orderRequest.getProductName())
                .quantity(orderRequest.getQuantity())
                .price(orderRequest.getPrice())
                .totalAmount(orderRequest.getPrice() * orderRequest.getQuantity())
                .customerName(orderRequest.getCustomerName())
                .orderDate(LocalDateTime.now())
                .status("PENDING")
                .build();

        log.info("📦 OrderEvent created: {} | Total: ${} | Customer: {}",
                orderId, orderEvent.getTotalAmount(), orderEvent.getCustomerName());

        // ─── Step 4: Send to JMS queue ───
        // After this call, the message is in Artemis.
        // The user gets an instant response while processing happens in the background!
        orderProducer.sendOrder(orderEvent);

        // ─── Step 5: Return response to user ───
        // Note: status is "ACCEPTED", not "COMPLETED"
        // The order is still being processed asynchronously by the consumer
        OrderResponse response = OrderResponse.builder()
                .message("Order placed successfully! It will be processed in the background.")
                .orderId(orderId)
                .status("ACCEPTED")
                .timestamp(LocalDateTime.now())
                .build();

        log.info("✅ Order [{}] accepted and queued for processing!", orderId);
        return response;
    }

    /**
     * Validates the order request.
     * In production, you'd use @Valid with Jakarta Bean Validation annotations.
     * For learning clarity, we validate manually with descriptive error messages.
     */
    private void validateOrderRequest(OrderRequest request) {
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new IllegalArgumentException("Product name is required!");
        }
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1!");
        }
        if (request.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0!");
        }
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required!");
        }
        log.debug("✅ Order request validation passed");
    }
}
