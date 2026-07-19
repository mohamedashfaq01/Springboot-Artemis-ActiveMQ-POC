package com.learnwithashfaq.artemis.producer;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * OrderProducer — Sends order messages to the Artemis JMS queue.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHAT IS JmsTemplate?
 * ═══════════════════════════════════════════════════════════════
 *
 * JmsTemplate is Spring's helper class for JMS operations.
 * Think of it like RestTemplate, but for message queues:
 *
 *   RestTemplate  → sends HTTP requests to REST APIs
 *   JmsTemplate   → sends messages to JMS queues
 *
 * Under the hood, JmsTemplate:
 *   1. Gets a connection from the ConnectionFactory
 *   2. Creates a JMS Session
 *   3. Creates a MessageProducer
 *   4. Converts your object to a JMS Message (using our Jackson converter)
 *   5. Sends the message to the specified destination (queue)
 *   6. Closes the session and connection
 *
 * You don't need to manage any of these steps manually!
 *
 * ═══════════════════════════════════════════════════════════════
 * THE convertAndSend() METHOD
 * ═══════════════════════════════════════════════════════════════
 *
 * jmsTemplate.convertAndSend("order-queue", orderEvent):
 *   - "order-queue" → The destination queue name (defined in broker.xml)
 *   - orderEvent    → The Java object to send
 *
 * The Jackson message converter (from JmsConfig) automatically:
 *   1. Serializes orderEvent → JSON string
 *   2. Wraps it in a JMS TextMessage
 *   3. Adds "_type" header with the class name
 *   4. Sends it to the "order-queue" in Artemis
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderProducer {

    /** Queue name — matches the queue defined in broker.xml */
    private static final String ORDER_QUEUE = "order-queue";

    /** Spring's JMS helper — auto-configured by spring-boot-starter-artemis */
    private final JmsTemplate jmsTemplate;

    /**
     * Sends an order event to the Artemis queue.
     *
     * @param orderEvent The order to be processed asynchronously
     */
    public void sendOrder(OrderEvent orderEvent) {

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║           📤 JMS PRODUCER — SENDING ORDER       ║");
        log.info("╠══════════════════════════════════════════════════╣");
        log.info("║ Order ID    : {}",  orderEvent.getOrderId());
        log.info("║ Product     : {}",  orderEvent.getProductName());
        log.info("║ Quantity    : {}",  orderEvent.getQuantity());
        log.info("║ Total       : ${}", orderEvent.getTotalAmount());
        log.info("║ Customer    : {}",  orderEvent.getCustomerName());
        log.info("║ Destination : {}",  ORDER_QUEUE);
        log.info("╚══════════════════════════════════════════════════╝");

        // This is where the magic happens!
        // convertAndSend = convert (object → JSON) + send (to queue)
        jmsTemplate.convertAndSend(ORDER_QUEUE, orderEvent);

        log.info("✅ Order [{}] sent to queue '{}' successfully!",
                orderEvent.getOrderId(), ORDER_QUEUE);
    }
}
