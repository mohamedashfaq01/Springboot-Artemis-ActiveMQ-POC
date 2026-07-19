package com.learnwithashfaq.artemis.exception;

/**
 * InventoryException — Thrown when inventory reservation fails.
 *
 * Real-world scenarios:
 *   - Product is out of stock
 *   - Warehouse system is temporarily unavailable
 *   - Insufficient quantity available
 *
 * In our simulation, this is triggered by ordering product "FAIL_INVENTORY".
 */
public class InventoryException extends OrderProcessingException {

    public InventoryException(String message) {
        super(message);
    }

    public InventoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
