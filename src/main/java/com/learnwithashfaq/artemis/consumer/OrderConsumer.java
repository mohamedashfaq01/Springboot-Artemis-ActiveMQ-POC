package com.learnwithashfaq.artemis.consumer;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.exception.InvalidDataException;
import com.learnwithashfaq.artemis.exception.OrderProcessingException;
import com.learnwithashfaq.artemis.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * OrderConsumer — Listens to the Artemis queue and processes orders.
 *
 * ═══════════════════════════════════════════════════════════════
 * MANUAL ERROR HANDLING IN CODE
 * ═══════════════════════════════════════════════════════════════
 *
 * This version of the consumer DOES NOT rely on Artemis broker for retries.
 * Instead, it manages retries and dead lettering manually:
 *
 * 1. Success: Message processed normally.
 * 2. Recoverable Error (e.g. InventoryException): 
 *    - Caught by consumer.
 *    - If retry count < 3: Sent to ERROR_QUEUE for delayed retry.
 *    - If retry count >= 3: Sent to DEAD_LETTER_QUEUE.
 * 3. Fatal Error (e.g. InvalidDataException):
 *    - Caught by consumer.
 *    - Sent straight to DEAD_LETTER_QUEUE.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderProcessingService orderProcessingService;
    private final JmsTemplate jmsTemplate;

    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final String ERROR_QUEUE = "ERROR_QUEUE";
    private static final String DEAD_LETTER_QUEUE = "DEAD_LETTER_QUEUE";

    @JmsListener(destination = "order-queue")
    public void receiveOrder(
            @Payload OrderEvent orderEvent,
            @Header(name = "RetryCount", defaultValue = "0") int retryCount) {

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║           📥 JMS CONSUMER — MESSAGE RECEIVED    ║");
        log.info("╠══════════════════════════════════════════════════╣");
        log.info("║ Order ID       : {}", orderEvent.getOrderId());
        log.info("║ Product        : {}", orderEvent.getProductName());
        log.info("║ Quantity       : {}", orderEvent.getQuantity());
        log.info("║ Total Amount   : ${}", orderEvent.getTotalAmount());
        log.info("║ Customer       : {}", orderEvent.getCustomerName());
        log.info("║ Manual Retries : {} of {}", retryCount, MAX_DELIVERY_ATTEMPTS);
        log.info("╚══════════════════════════════════════════════════╝");

        try {
            // Process the order
            orderProcessingService.processOrder(orderEvent);

            log.info("╔══════════════════════════════════════════════════╗");
            log.info("║     ✅ ORDER PROCESSED SUCCESSFULLY!            ║");
            log.info("║     Order ID: {}", orderEvent.getOrderId());
            log.info("╚══════════════════════════════════════════════════╝");

        } catch (InvalidDataException ex) {
            // FATAL ERROR -> Direct to DLQ without retries
            log.error("❌ FATAL ERROR for Order [{}]: {}. Moving to DEAD_LETTER_QUEUE.", 
                    orderEvent.getOrderId(), ex.getMessage());
            
            jmsTemplate.convertAndSend(DEAD_LETTER_QUEUE, orderEvent, message -> {
                message.setStringProperty("ErrorReason", "InvalidDataException: " + ex.getMessage());
                message.setIntProperty("RetryCount", 0);
                return message;
            });
            
        } catch (OrderProcessingException ex) {
            // RECOVERABLE ERROR -> Route to ERROR_QUEUE or DEAD_LETTER_QUEUE
            log.error("❌ PROCESSING FAILED for Order [{}]: {}", orderEvent.getOrderId(), ex.getMessage());

            if (retryCount < MAX_DELIVERY_ATTEMPTS) {
                log.warn("🔄 Routing Order [{}] to ERROR_QUEUE for attempt #{}", 
                        orderEvent.getOrderId(), retryCount + 1);
                
                jmsTemplate.convertAndSend(ERROR_QUEUE, orderEvent, message -> {
                    message.setIntProperty("RetryCount", retryCount); // ErrorConsumer will increment it before sending back
                    message.setStringProperty("ErrorReason", ex.getMessage());
                    return message;
                });
            } else {
                log.error("⚠️ MAX RETRIES EXHAUSTED for Order [{}]! Moving to DEAD_LETTER_QUEUE.", 
                        orderEvent.getOrderId());
                
                jmsTemplate.convertAndSend(DEAD_LETTER_QUEUE, orderEvent, message -> {
                    message.setIntProperty("RetryCount", retryCount);
                    message.setStringProperty("ErrorReason", "Max retries exhausted. Last error: " + ex.getMessage());
                    return message;
                });
            }
        }
        
        // Notice we DO NOT rethrow the exception. 
        // This allows the current JMS transaction to COMMIT, removing the message 
        // from 'order-queue' since we have manually routed it elsewhere.
    }
}
