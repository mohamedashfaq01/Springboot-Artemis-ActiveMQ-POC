package com.learnwithashfaq.artemis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OrderRequest — What the user sends when placing an order via REST API.
 *
 * ═══════════════════════════════════════════════════════════════
 * EXAMPLE JSON (what you'd send from Postman):
 * ═══════════════════════════════════════════════════════════════
 *
 * POST /api/orders
 * {
 *     "productName": "iPhone 15 Pro",
 *     "quantity": 2,
 *     "price": 999.99,
 *     "customerName": "Ashfaq"
 * }
 *
 * ═══════════════════════════════════════════════════════════════
 * LOMBOK ANNOTATIONS EXPLAINED:
 * ═══════════════════════════════════════════════════════════════
 *
 * @Data → Generates: getters, setters, toString(), equals(), hashCode()
 *   - Equivalent to writing 20+ lines of boilerplate code!
 *
 * @Builder → Generates the Builder pattern:
 *   OrderRequest.builder()
 *       .productName("iPhone")
 *       .quantity(2)
 *       .build();
 *
 * @NoArgsConstructor → Generates: public OrderRequest() {}
 *   - Required by Jackson for JSON deserialization
 *
 * @AllArgsConstructor → Generates: public OrderRequest(String productName, int quantity, ...)
 *   - Required by @Builder to work with all fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    /**
     * Name of the product being ordered.
     *
     * SPECIAL VALUES FOR TESTING:
     * - "FAIL_PAYMENT"   → Simulates payment failure (triggers retry + DLQ)
     * - "FAIL_INVENTORY" → Simulates inventory shortage
     * - Anything else    → Normal successful order
     */
    private String productName;

    /** Number of items to order. */
    private int quantity;

    /** Price per unit in USD. */
    private double price;

    /** Name of the customer placing the order. */
    private String customerName;
}
