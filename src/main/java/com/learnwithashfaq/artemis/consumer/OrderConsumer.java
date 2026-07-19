package com.learnwithashfaq.artemis.consumer;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.exception.OrderProcessingException;
import com.learnwithashfaq.artemis.service.OrderProcessingService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * OrderConsumer — Listens to the Artemis queue and processes orders.
 *
 * ═══════════════════════════════════════════════════════════════
 * THIS IS WHERE THE JMS TRANSACTION MAGIC HAPPENS!
 * ═══════════════════════════════════════════════════════════════
 *
 * How @JmsListener works with transactions:
 *
 * 1. Spring picks up a message from "order-queue"
 * 2. A JMS transaction is STARTED automatically
 *    (because we set session-transacted=true in application.yml)
 * 3. The message is deserialized from JSON → OrderEvent
 * 4. This method is called with the OrderEvent
 * 5. OrderProcessingService.processOrder() runs the 3 steps
 *
 * ─── IF ALL STEPS SUCCEED: ───
 *   → JMS transaction COMMITS
 *   → Message is ACKNOWLEDGED (removed from queue permanently)
 *   → The message is gone forever ✅
 *
 * ─── IF ANY STEP THROWS AN EXCEPTION: ───
 *   → JMS transaction ROLLS BACK
 *   → Message is NOT acknowledged
 *   → Message goes BACK to the queue
 *   → Artemis waits for redelivery-delay (5 seconds)
 *   → Message is REDELIVERED to this listener
 *   → JMSXDeliveryCount increments (1 → 2 → 3)
 *   → After max-delivery-attempts (3), message goes to DLQ
 *
 * ═══════════════════════════════════════════════════════════════
 * VISUAL FLOW:
 * ═══════════════════════════════════════════════════════════════
 *
 *   [order-queue] → Consumer picks up message
 *                    │
 *                    ├── processOrder() succeeds → COMMIT → message removed ✅
 *                    │
 *                    └── processOrder() fails → ROLLBACK → message back in queue
 *                                                │
 *                                                ├── Delivery #2 → fails → ROLLBACK
 *                                                │
 *                                                ├── Delivery #3 → fails → ROLLBACK
 *                                                │
 *                                                └── Max retries exceeded → DLQ 💀
 *
 * ═══════════════════════════════════════════════════════════════
 * KEY CONCEPT: JMSXDeliveryCount
 * ═══════════════════════════════════════════════════════════════
 *
 * This is a JMS standard header that Artemis sets automatically.
 * It tells you how many times this message has been delivered:
 *
 *   JMSXDeliveryCount = 1 → First delivery (original attempt)
 *   JMSXDeliveryCount = 2 → Second delivery (1st retry)
 *   JMSXDeliveryCount = 3 → Third delivery (2nd retry — LAST CHANCE)
 *
 * We read this to log which attempt we're on. Very useful for debugging!
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderProcessingService orderProcessingService;

    /** Maximum delivery attempts (must match broker.xml max-delivery-attempts) */
    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    /**
     * Listens to "order-queue" and processes incoming orders.
     *
     * ANNOTATION EXPLAINED:
     *
     * @JmsListener(destination = "order-queue")
     *   → Tells Spring to poll "order-queue" for new messages
     *   → When a message arrives, deserialize it and call this method
     *   → Runs inside a JMS transaction (because session-transacted=true)
     *
     * @Payload OrderEvent orderEvent
     *   → The deserialized message body (JSON → OrderEvent)
     *
     * @Header(name = "JMSXDeliveryCount", defaultValue = "1")
     *   → Reads the delivery count header from the JMS message
     *   → First delivery = 1, first retry = 2, etc.
     *   → defaultValue = "1" prevents NPE if header is missing
     *
     * @param message — The raw JMS Message object (for accessing additional properties)
     */
    @JmsListener(destination = "order-queue")
    public void receiveOrder(
            @Payload OrderEvent orderEvent,
            @Header(name = "JMSXDeliveryCount", defaultValue = "1") int deliveryCount,
            Message message) {

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║           📥 JMS CONSUMER — MESSAGE RECEIVED    ║");
        log.info("╠══════════════════════════════════════════════════╣");
        log.info("║ Order ID       : {}", orderEvent.getOrderId());
        log.info("║ Product        : {}", orderEvent.getProductName());
        log.info("║ Quantity       : {}", orderEvent.getQuantity());
        log.info("║ Total Amount   : ${}", orderEvent.getTotalAmount());
        log.info("║ Customer       : {}", orderEvent.getCustomerName());
        log.info("║ Delivery Count : {} of {} (attempt #{} of {})",
                deliveryCount, MAX_DELIVERY_ATTEMPTS,
                deliveryCount, MAX_DELIVERY_ATTEMPTS);
        log.info("╚══════════════════════════════════════════════════╝");

        // Log retry information if this is a redelivered message
        if (deliveryCount > 1) {
            log.warn("🔄 RETRY detected! This is delivery attempt #{} for Order [{}]. " +
                     "Previous attempt(s) failed.",
                    deliveryCount, orderEvent.getOrderId());

            if (deliveryCount == MAX_DELIVERY_ATTEMPTS) {
                log.warn("⚠️ LAST ATTEMPT! If this fails, Order [{}] will be moved to DLQ!",
                        orderEvent.getOrderId());
            }
        }

        try {
            /*
             * ═══════════════════════════════════════════════════════
             * TRANSACTION BOUNDARY — This is the critical section
             * ═══════════════════════════════════════════════════════
             *
             * Everything inside processOrder() runs within the JMS transaction:
             *   1. Save Order
             *   2. Reserve Inventory
             *   3. Process Payment
             *
             * If processOrder() returns normally → transaction COMMITS
             * If processOrder() throws exception → transaction ROLLS BACK
             *
             * We DON'T need @Transactional here because JMS transactions
             * are managed by the JMS Session, not Spring's transaction manager.
             * The session-transacted=true setting handles this automatically.
             */
            orderProcessingService.processOrder(orderEvent);

            // If we reach here, all 3 steps succeeded!
            log.info("╔══════════════════════════════════════════════════╗");
            log.info("║     ✅ ORDER PROCESSED SUCCESSFULLY!            ║");
            log.info("║     Order ID: {}", orderEvent.getOrderId());
            log.info("║     JMS Transaction will COMMIT.                ║");
            log.info("║     Message will be REMOVED from queue.         ║");
            log.info("╚══════════════════════════════════════════════════╝");

        } catch (OrderProcessingException ex) {
            /*
             * ═══════════════════════════════════════════════════════
             * FAILURE HANDLING — Exception triggers JMS rollback
             * ═══════════════════════════════════════════════════════
             *
             * By RE-THROWING the exception, we tell Spring JMS:
             * "This processing failed — roll back the transaction!"
             *
             * Spring JMS will then:
             *   1. NOT acknowledge the message
             *   2. Roll back the JMS session
             *   3. Artemis puts the message back in the queue
             *   4. After redelivery-delay, message is redelivered
             *
             * IMPORTANT: We MUST re-throw the exception!
             * If we swallow it (catch without re-throw), Spring thinks
             * processing succeeded and commits the transaction.
             * The message would be lost forever!
             */
            log.error("╔══════════════════════════════════════════════════╗");
            log.error("║     ❌ ORDER PROCESSING FAILED!                 ║");
            log.error("║     Order ID: {}", orderEvent.getOrderId());
            log.error("║     Error: {}", ex.getMessage());
            log.error("║     Attempt: {} of {}",
                    deliveryCount, MAX_DELIVERY_ATTEMPTS);

            if (deliveryCount < MAX_DELIVERY_ATTEMPTS) {
                log.error("║     Action: JMS Transaction will ROLLBACK.      ║");
                log.error("║     Message will be REDELIVERED after delay.    ║");
            } else {
                log.error("║     ⚠️ MAX RETRIES EXHAUSTED!                  ║");
                log.error("║     Message will be moved to DLQ.              ║");
                log.error("║     Manual intervention required!              ║");
            }
            log.error("╚══════════════════════════════════════════════════╝");

            // RE-THROW to trigger JMS transaction rollback!
            // This is CRITICAL — do NOT swallow this exception!
            throw ex;
        }
    }
}
