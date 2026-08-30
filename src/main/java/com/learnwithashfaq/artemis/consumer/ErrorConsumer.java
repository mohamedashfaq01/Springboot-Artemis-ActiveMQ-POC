package com.learnwithashfaq.artemis.consumer;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ErrorConsumer — Manages manual retries by listening to the ERROR_QUEUE.
 * 
 * Instead of relying on Artemis broker retries, we handle it in code:
 * 1. Reads the message from ERROR_QUEUE.
 * 2. Delays processing to simulate backoff.
 * 3. Sends it back to the main order-queue for another attempt.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ErrorConsumer {

    private final JmsTemplate jmsTemplate;
    
    // Constant for our main queue
    private static final String ORDER_QUEUE = "order-queue";
    
    // The delay time for retrying (e.g. 5 seconds for production). 
    // In our tests we want it to be fast, so we'll just wait 500ms.
    private static final long RETRY_DELAY_MS = 500;

    @JmsListener(destination = "ERROR_QUEUE")
    public void processErrorQueue(
            @Payload OrderEvent orderEvent,
            @Header(name = "RetryCount", defaultValue = "0") int retryCount) {
        
        log.warn("⚠️ ERROR_QUEUE received order [{}]. Current retry count: {}", 
                orderEvent.getOrderId(), retryCount);

        try {
            // Manual delay (simulate backoff before retrying)
            log.info("⏳ Waiting {}ms before sending back to main queue...", RETRY_DELAY_MS);
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Delay interrupted", e);
        }

        // Send back to the main order queue, preserving the retry count.
        // We use JmsTemplate.convertAndSend and pass a MessagePostProcessor
        // to inject our custom RetryCount header so the main consumer knows.
        log.info("🔄 Re-queueing order [{}] to '{}' for attempt #{}", 
                orderEvent.getOrderId(), ORDER_QUEUE, retryCount + 1);
                
        jmsTemplate.convertAndSend(ORDER_QUEUE, orderEvent, message -> {
            message.setIntProperty("RetryCount", retryCount + 1);
            return message;
        });
    }
}
