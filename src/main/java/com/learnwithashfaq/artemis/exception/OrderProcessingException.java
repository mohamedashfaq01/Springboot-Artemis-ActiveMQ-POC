package com.learnwithashfaq.artemis.exception;

/**
 * OrderProcessingException — Generic exception for order processing failures.
 *
 * This is the parent exception for all order-related failures.
 * When thrown inside a @JmsListener, it triggers:
 *   1. JMS transaction rollback
 *   2. Message redelivery (retry)
 *   3. Eventually → DLQ after max retries
 */
public class OrderProcessingException extends RuntimeException {

    public OrderProcessingException(String message) {
        super(message);
    }

    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
