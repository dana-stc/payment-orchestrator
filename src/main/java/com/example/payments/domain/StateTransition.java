package com.example.payments.domain;

import java.time.Instant;

/** One entry in a payment's audit trail. {@code from} is null for the initial creation. */
public record StateTransition(PaymentState from, PaymentState to, String reason, Instant at) {
}
