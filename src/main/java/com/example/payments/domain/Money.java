package com.example.payments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount. The scale is always the currency's number of fraction digits;
 * an amount with more precision than the currency allows is rejected, never silently rounded.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        try {
            amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "amount %s has more decimals than %s allows".formatted(amount, currency), e);
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /** Multiplies by a factor (e.g. a fee rate), rounding half-even to the currency's scale. */
    public Money times(BigDecimal factor) {
        var scaled = amount.multiply(factor).setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
        return new Money(scaled, currency);
    }

    public long minorUnits() {
        return amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }
}
