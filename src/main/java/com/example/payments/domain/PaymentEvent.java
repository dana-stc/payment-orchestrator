package com.example.payments.domain;

/** Domain events written to the outbox and later published to the message broker. */
public sealed interface PaymentEvent {

    PaymentId paymentId();

    Money amount();

    record Authorized(PaymentId paymentId, Money amount) implements PaymentEvent {
    }

    record Captured(PaymentId paymentId, Money amount) implements PaymentEvent {
    }

    record Failed(PaymentId paymentId, Money amount, String reason) implements PaymentEvent {
    }

    default String type() {
        return switch (this) {
            case Authorized a -> "PaymentAuthorized";
            case Captured c -> "PaymentCaptured";
            case Failed f -> "PaymentFailed";
        };
    }
}
