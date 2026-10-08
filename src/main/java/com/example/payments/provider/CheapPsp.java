package com.example.payments.provider;

import com.example.payments.domain.Money;
import com.example.payments.domain.ProviderResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Set;
import java.util.UUID;

/** Mock: slower but cheaper (1.0%), only RON and EUR. Declines amounts >= 5 000. */
public class CheapPsp implements PaymentProvider {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.010");
    private static final Set<String> CURRENCIES = Set.of("RON", "EUR");

    @Override
    public String name() {
        return "CheapPsp";
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
        simulateLatency(200);
        if (request.amount().amount().compareTo(new BigDecimal("5000")) >= 0) {
            return new ProviderResult.Declined("amount over limit", "LIMIT_EXCEEDED");
        }
        return new ProviderResult.Approved("cheap-" + UUID.randomUUID(), Instant.now());
    }

    private static void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
