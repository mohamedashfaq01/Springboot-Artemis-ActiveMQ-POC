package com.learnwithashfaq.artemis.service;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.entity.OrderEntity;
import com.learnwithashfaq.artemis.exception.InventoryException;
import com.learnwithashfaq.artemis.exception.InvalidDataException;
import com.learnwithashfaq.artemis.exception.PaymentException;
import com.learnwithashfaq.artemis.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * OrderProcessingService — The 3-step order processing pipeline.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHAT CHANGED FROM THE SIMULATION VERSION?
 * ═══════════════════════════════════════════════════════════════
 *
 * Previously, saveOrder() just logged "saved". Now it ACTUALLY:
 *   1. Creates an OrderEntity from the OrderEvent
 *   2. Persists it to H2 database via OrderRepository
 *   3. Updates the status to COMPLETED after all steps pass
 *
 * The failure simulation (FAIL_PAYMENT, FAIL_INVENTORY) still works
 * exactly the same way — throwing exceptions that trigger JMS rollback.
 *
 * ═══════════════════════════════════════════════════════════════
 * IDEMPOTENCY — WHY IT MATTERS NOW
 * ═══════════════════════════════════════════════════════════════
 *
 * With a real database, retries become a problem:
 *   Attempt 1: saveOrder() ✅ → reserveInventory() ✅ → processPayment() ❌
 *   Attempt 2: saveOrder() ← Would INSERT a DUPLICATE row!
 *
 * To handle this, saveOrder() checks if the order already exists:
 *   - If orderId exists → skip insert (idempotent)
 *   - If orderId is new → insert new row
 *
 * This is how enterprise systems handle JMS retries safely.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProcessingService {

    private final OrderRepository orderRepository;

    /**
     * Processes an order through all 3 steps.
     * Called by the JMS Consumer inside a JMS transaction.
     *
     * @param orderEvent The order to process
     * @throws InventoryException if stock reservation fails
     * @throws PaymentException   if payment processing fails
     */
    public void processOrder(OrderEvent orderEvent) {
        log.info("┌─────────────────────────────────────────────────┐");
        log.info("│     🔄 STARTING ORDER PROCESSING PIPELINE       │");
        log.info("│     Order ID: {}",  orderEvent.getOrderId());
        log.info("└─────────────────────────────────────────────────┘");

        // ⚠️ DELIBERATE FATAL ERROR for testing unrecoverable exceptions
        if ("FATAL_ERROR".equalsIgnoreCase(orderEvent.getProductName())) {
            log.error("❌ FATAL DATA CORRUPTION DETECTED for Order [{}]", orderEvent.getOrderId());
            throw new InvalidDataException("Product name indicates a fatal unrecoverable error.");
        }

        // ═══ STEP 1: SAVE ORDER ═══
        saveOrder(orderEvent);

        // ═══ STEP 2: RESERVE INVENTORY ═══
        reserveInventory(orderEvent);

        // ═══ STEP 3: PROCESS PAYMENT ═══
        processPayment(orderEvent);

        // ═══ ALL STEPS PASSED — Mark order as COMPLETED ═══
        updateOrderStatus(orderEvent.getOrderId(), "COMPLETED");

        log.info("┌─────────────────────────────────────────────────┐");
        log.info("│     ✅ ALL 3 STEPS COMPLETED SUCCESSFULLY!      │");
        log.info("│     Order [{}] → COMPLETED", orderEvent.getOrderId());
        log.info("└─────────────────────────────────────────────────┘");
    }

    /**
     * STEP 1: Save Order to Database (IDEMPOTENT).
     *
     * Checks if the order already exists (from a previous retry attempt).
     * If it does, we skip the insert to avoid duplicates.
     * If it doesn't, we create a new OrderEntity and persist it.
     *
     * STATUS FLOW:
     *   saveOrder()      → Status: PROCESSING
     *   processPayment() → Status: COMPLETED (updated after all steps)
     */
    private void saveOrder(OrderEvent orderEvent) {
        log.info("📝 STEP 1/3 — Saving Order [{}] to database...", orderEvent.getOrderId());

        // IDEMPOTENCY CHECK: Has this order been saved by a previous retry?
        Optional<OrderEntity> existingOrder = orderRepository.findByOrderId(orderEvent.getOrderId());

        if (existingOrder.isPresent()) {
            log.info("📝 STEP 1/3 — ⏭️ Order [{}] already exists in database (retry detected). " +
                     "Skipping insert. Current status: {}",
                    orderEvent.getOrderId(), existingOrder.get().getStatus());

            // Reset status back to PROCESSING for this retry attempt
            updateOrderStatus(orderEvent.getOrderId(), "PROCESSING");
            return;
        }

        // Build the JPA entity from the event DTO
        OrderEntity entity = OrderEntity.builder()
                .orderId(orderEvent.getOrderId())
                .productName(orderEvent.getProductName())
                .quantity(orderEvent.getQuantity())
                .price(orderEvent.getPrice())
                .totalAmount(orderEvent.getTotalAmount())
                .customerName(orderEvent.getCustomerName())
                .orderDate(orderEvent.getOrderDate())
                .status("PROCESSING")
                .build();

        orderRepository.save(entity);

        log.info("📝 STEP 1/3 — ✅ Order [{}] saved to database successfully! Status: PROCESSING",
                orderEvent.getOrderId());
    }

    /**
     * STEP 2: Reserve Inventory.
     *
     * In a real application, this would call an inventory microservice.
     * For testing: product "FAIL_INVENTORY" triggers failure.
     */
    private void reserveInventory(OrderEvent orderEvent) {
        log.info("📦 STEP 2/3 — Reserving {} units of '{}' in inventory...",
                orderEvent.getQuantity(), orderEvent.getProductName());

        // ⚠️ DELIBERATE FAILURE for testing
        if ("FAIL_INVENTORY".equalsIgnoreCase(orderEvent.getProductName())) {
            log.error("📦 STEP 2/3 — ❌ INVENTORY FAILURE for Order [{}]! " +
                      "Product '{}' is out of stock!",
                    orderEvent.getOrderId(), orderEvent.getProductName());

            throw new InventoryException(
                    String.format("Insufficient stock for product '%s'. " +
                                  "Requested: %d, Available: 0",
                            orderEvent.getProductName(), orderEvent.getQuantity()));
        }

        log.info("📦 STEP 2/3 — ✅ Inventory reserved for Order [{}]: {} units of '{}'",
                orderEvent.getOrderId(), orderEvent.getQuantity(), orderEvent.getProductName());
    }

    /**
     * STEP 3: Process Payment.
     *
     * In a real application, this would call a payment gateway API.
     * For testing: product "FAIL_PAYMENT" triggers failure.
     */
    private void processPayment(OrderEvent orderEvent) {
        log.info("💳 STEP 3/3 — Processing payment of ${} for Order [{}]...",
                orderEvent.getTotalAmount(), orderEvent.getOrderId());

        // ⚠️ DELIBERATE FAILURE for testing
        if ("FAIL_PAYMENT".equalsIgnoreCase(orderEvent.getProductName())) {
            log.error("💳 STEP 3/3 — ❌ PAYMENT FAILURE for Order [{}]! " +
                      "Payment gateway declined the transaction. Amount: ${}",
                    orderEvent.getOrderId(), orderEvent.getTotalAmount());

            throw new PaymentException(
                    String.format("Payment declined for Order '%s'. Amount: $%.2f. " +
                                  "Reason: Insufficient funds.",
                            orderEvent.getOrderId(), orderEvent.getTotalAmount()));
        }

        log.info("💳 STEP 3/3 — ✅ Payment of ${} processed for Order [{}]",
                orderEvent.getTotalAmount(), orderEvent.getOrderId());
    }

    /**
     * Updates the status of an existing order in the database.
     *
     * Called after all 3 steps succeed to mark the order as COMPLETED.
     * Also used during retries to reset status back to PROCESSING.
     */
    private void updateOrderStatus(String orderId, String newStatus) {
        orderRepository.findByOrderId(orderId).ifPresent(order -> {
            String oldStatus = order.getStatus();
            order.setStatus(newStatus);
            orderRepository.save(order);
            log.info("📋 Order [{}] status updated: {} → {}", orderId, oldStatus, newStatus);
        });
    }
}
