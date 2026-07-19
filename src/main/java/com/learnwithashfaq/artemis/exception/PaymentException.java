package com.learnwithashfaq.artemis.exception;

/**
 * PaymentException — Thrown when payment processing fails.
 *
 * Real-world scenarios:
 *   - Insufficient funds
 *   - Payment gateway timeout
 *   - Card declined
 *   - Fraud detection triggered
 *
 * In our simulation, this is triggered by ordering product "FAIL_PAYMENT".
 */
public class PaymentException extends OrderProcessingException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
