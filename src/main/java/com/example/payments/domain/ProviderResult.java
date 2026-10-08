package com.example.payments.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Outcome of asking a provider to authorize a payment. Sealed, so every {@code switch}
 * over it must handle all cases or the code does not compile.
 */
public sealed interface ProviderResult {

    record Approved(String providerRef, Instant at) implements ProviderResult {
    }

    /** Definitive refusal (insufficient funds, fraud...). Never retried on another provider. */
    record Declined(String reason, String code) implements ProviderResult {
    }

    /** No answer in time; the outcome is unknown. Eligible for fallback. */
    record Timeout(Duration after) implements ProviderResult {
    }

    /** Provider is down or returned an unusable response. Eligible for fallback. */
    record Unavailable(String reason) implements ProviderResult {
    }

    /** Whether it is safe and useful to try the next provider. */
    default boolean retryableOnAnotherProvider() {
        return switch (this) {
            case Approved a -> false;
            case Declined d -> false;
            case Timeout t -> true;
            case Unavailable u -> true;
        };
    }
}
