package com.example.payments.application;

/** The same Idempotency-Key was reused with a different request body. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '%s' was already used with a different request".formatted(key));
    }
}
