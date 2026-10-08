package com.example.payments.domain;

import java.util.Set;

public enum PaymentState {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELLED,
    FAILED;

    /** The state machine: which states may follow this one. */
    public Set<PaymentState> allowedNext() {
        return switch (this) {
            case CREATED -> Set.of(AUTHORIZED, FAILED);
            case AUTHORIZED -> Set.of(CAPTURED, CANCELLED);
            case CAPTURED -> Set.of(PARTIALLY_REFUNDED, REFUNDED);
            case PARTIALLY_REFUNDED -> Set.of(REFUNDED);
            case REFUNDED, CANCELLED, FAILED -> Set.of();
        };
    }

    public boolean canTransitionTo(PaymentState next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
