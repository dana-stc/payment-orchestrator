package com.example.payments.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;

/**
 * Payment aggregate. Immutable: every change returns a new instance, and every state change
 * goes through {@link #transitionTo} so the state machine cannot be bypassed.
 */
public record Payment(
        PaymentId id,
        String idempotencyKey,
        String merchantId,
        Money amount,
        PaymentState state,
        String provider,
        String providerRef,
        SequencedCollection<StateTransition> history) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(state, "state");
        history = List.copyOf(history);
    }

    public static Payment create(String idempotencyKey, String merchantId, Money amount, Instant now) {
        var created = new StateTransition(null, PaymentState.CREATED, "created", now);
        return new Payment(PaymentId.random(), idempotencyKey, merchantId, amount,
                PaymentState.CREATED, null, null, List.of(created));
    }

    /** Records the provider's answer. The switch is exhaustive over the sealed {@link ProviderResult}. */
    public Payment applyProviderResult(String providerName, ProviderResult result, Instant now) {
        return switch (result) {
            case ProviderResult.Approved(var ref, var at) ->
                    transitionTo(PaymentState.AUTHORIZED, "approved by " + providerName, now)
                            .withProvider(providerName, ref);
            case ProviderResult.Declined(var reason, var code) ->
                    transitionTo(PaymentState.FAILED, "declined by %s: %s (%s)".formatted(providerName, reason, code), now)
                            .withProvider(providerName, null);
            case ProviderResult.Timeout(var after) ->
                    transitionTo(PaymentState.FAILED, "provider timeout after " + after, now);
            case ProviderResult.Unavailable(var reason) ->
                    transitionTo(PaymentState.FAILED, "provider unavailable: " + reason, now);
        };
    }

    public Payment transitionTo(PaymentState next, String reason, Instant now) {
        if (!state.canTransitionTo(next)) {
            throw new InvalidStateTransitionException(id, state, next);
        }
        var newHistory = new ArrayList<>(history);
        newHistory.add(new StateTransition(state, next, reason, now));
        return new Payment(id, idempotencyKey, merchantId, amount, next, provider, providerRef, newHistory);
    }

    public StateTransition lastTransition() {
        return history.getLast();
    }

    private Payment withProvider(String newProvider, String newProviderRef) {
        return new Payment(id, idempotencyKey, merchantId, amount, state, newProvider, newProviderRef, history);
    }
}
