package com.learnwithashfaq.artemis.service;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.exception.InventoryException;
import com.learnwithashfaq.artemis.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OrderProcessingService — Simulates the 3-step order processing pipeline.
 *
 * ═══════════════════════════════════════════════════════════════
 * THE 3 STEPS (TRANSACTIONAL):
 * ═══════════════════════════════════════════════════════════════
 *
 *   Step 1: SAVE ORDER      → Persist order to database
 *   Step 2: RESERVE STOCK   → Reserve inventory in warehouse
 *   Step 3: PROCESS PAYMENT → Charge the customer
 *
 * If ANY step fails, the entire JMS transaction rolls back.
 * The message goes back to the queue and gets retried.
 *
 * ═══════════════════════════════════════════════════════════════
 * HOW TO TRIGGER FAILURES (FOR TESTING):
 * ═══════════════════════════════════════════════════════════════
 *
 * Use these special product names when calling the API:
 *
 *   "FAIL_PAYMENT"   → Steps 1 & 2 succeed, Step 3 fails
 *                       Shows: message retry + DLQ after 3 attempts
 *
 *   "FAIL_INVENTORY" → Step 1 succeeds, Step 2 fails
 *                       Shows: earlier failure, same retry behavior
 *
 *   "iPhone 15 Pro"  → All steps succeed → order completed! ✅
 *   (or any other name)
 *
 * ═══════════════════════════════════════════════════════════════
 * WHY SIMULATE INSTEAD OF USING REAL DB/PAYMENT?
 * ═══════════════════════════════════════════════════════════════
 *
 * This keeps the focus on JMS concepts (transactions, retries, DLQ)
 * without the complexity of setting up databases, payment gateways, etc.
 * In production, these methods would call real repositories and APIs.
 */
@Service
@Slf4j
public class OrderProcessingService {

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

        // ═══ STEP 1: SAVE ORDER ═══
        saveOrder(orderEvent);

        // ═══ STEP 2: RESERVE INVENTORY ═══
        reserveInventory(orderEvent);

        // ═══ STEP 3: PROCESS PAYMENT ═══
        processPayment(orderEvent);

        log.info("┌─────────────────────────────────────────────────┐");
        log.info("│     ✅ ALL 3 STEPS COMPLETED SUCCESSFULLY!      │");
        log.info("│     Order [{}] → COMPLETED", orderEvent.getOrderId());
        log.info("└─────────────────────────────────────────────────┘");
    }

    /**
     * STEP 1: Save Order to Database.
     *
     * In a real application, this would:
     *   - Call orderRepository.save(orderEntity)
     *   - Generate database sequence ID
     *   - Set initial status to PROCESSING
     *
     * For learning: we just log it.
     */
    private void saveOrder(OrderEvent orderEvent) {
        log.info("📝 STEP 1/3 — Saving Order [{}] to database...", orderEvent.getOrderId());

        // Simulate database save (takes ~100ms)
        simulateProcessingTime(100);

        log.info("📝 STEP 1/3 — ✅ Order [{}] saved to database successfully!",
                orderEvent.getOrderId());
    }

    /**
     * STEP 2: Reserve Inventory.
     *
     * In a real application, this would:
     *   - Call inventoryService.reserve(productId, quantity)
     *   - Decrement available stock in warehouse system
     *   - Throw InventoryException if out of stock
     *
     * For testing: Sending product "FAIL_INVENTORY" triggers failure here.
     */
    private void reserveInventory(OrderEvent orderEvent) {
        log.info("📦 STEP 2/3 — Reserving {} units of '{}' in inventory...",
                orderEvent.getQuantity(), orderEvent.getProductName());

        // Simulate inventory check (takes ~150ms)
        simulateProcessingTime(150);

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
     * In a real application, this would:
     *   - Call paymentGateway.charge(customerId, amount)
     *   - Handle payment gateway responses (approved, declined, timeout)
     *   - Throw PaymentException on failure
     *
     * For testing: Sending product "FAIL_PAYMENT" triggers failure here.
     *
     * NOTE: This is the LAST step. If it fails after Steps 1 & 2 succeeded,
     * the JMS transaction rolls back → message retried → Steps 1 & 2 run again.
     * This is why IDEMPOTENCY matters (covered in best practices).
     */
    private void processPayment(OrderEvent orderEvent) {
        log.info("💳 STEP 3/3 — Processing payment of ${} for Order [{}]...",
                orderEvent.getTotalAmount(), orderEvent.getOrderId());

        // Simulate payment gateway call (takes ~200ms)
        simulateProcessingTime(200);

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
     * Simulates processing time (like a database call or API request).
     * In real applications, this latency comes naturally from external systems.
     */
    private void simulateProcessingTime(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Processing interrupted!");
        }
    }
}
