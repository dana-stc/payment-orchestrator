package com.example.payments.provider;

import com.example.payments.domain.Money;
import com.example.payments.domain.ProviderResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

/**
 * Mock: fast but pricier (1.5%). Deterministic behaviour so it can be demoed and tested:
 * <ul>
 *   <li>amount ending in .13 (minor units % 100 == 13) -> Timeout (triggers fallback)</li>
 *   <li>amount >= 10 000 -> Declined</li>
 * </ul>
 */
public class FastPsp implements PaymentProvider {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.015");
    private static final Set<String> CURRENCIES = Set.of("RON", "EUR", "USD");

    @Override
    public String name() {
        return "FastPsp";
    }

    @Override
    public boolean supports(Currency currency) {
        return CURRENCIES.contains(currency.getCurrencyCode());
    }

    @Override
    public Money quoteFee(ProviderRequest request) {
        return request.amount().times(FEE_RATE);
    }

    @Override
    public ProviderResult authorize(ProviderRequest request) {
        simulateLatency(50);
        var amount = request.amount();
        if (amount.minorUnits() % 100 == 13) {
            return new ProviderResult.Timeout(Duration.ofSeconds(2));
        }
        if (amount.amount().compareTo(new BigDecimal("10000")) >= 0) {
            return new ProviderResult.Declined("amount over limit", "LIMIT_EXCEEDED");
        }
        return new ProviderResult.Approved("fast-" + UUID.randomUUID(), Instant.now());
    }

    private static void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
