package com.example.payments.api;

import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        String id,
        String merchantId,
        BigDecimal amount,
        String currency,
        PaymentState state,
        String provider,
        String providerRef,
        List<Transition> history) {

    public record Transition(PaymentState from, PaymentState to, String reason, Instant at) {
    }

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.id().toString(),
                p.merchantId(),
                p.amount().amount(),
                p.amount().currency().getCurrencyCode(),
                p.state(),
                p.provider(),
                p.providerRef(),
                p.history().stream()
                        .map(t -> new Transition(t.from(), t.to(), t.reason(), t.at()))
                        .toList());
    }
}
