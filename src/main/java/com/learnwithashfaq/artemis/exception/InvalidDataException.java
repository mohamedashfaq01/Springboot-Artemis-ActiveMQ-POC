package com.learnwithashfaq.artemis.exception;

/**
 * InvalidDataException — Unrecoverable error exception.
 * 
 * Thrown when the incoming message has invalid or corrupted data
 * that cannot possibly succeed upon retry (e.g., negative amounts, missing IDs).
 * 
 * We use this to route the message directly to the DEAD_LETTER_QUEUE.
 */
public class InvalidDataException extends RuntimeException {
    public InvalidDataException(String message) {
        super(message);
    }
}
