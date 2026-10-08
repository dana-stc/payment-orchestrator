package com.example.payments.domain;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(PaymentId id, PaymentState from, PaymentState to) {
        super("Payment %s cannot go from %s to %s".formatted(id, from, to));
    }
}
