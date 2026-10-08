package com.example.payments.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesScaleToCurrency() {
        assertThat(Money.of("10", "RON").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10", "RON").amount().scale()).isEqualTo(2);
    }

    @Test
    void equalAmountsWithDifferentInputScaleAreEqual() {
        assertThat(Money.of("10", "EUR")).isEqualTo(Money.of("10.00", "EUR"));
    }

    @Test
    void rejectsMoreDecimalsThanCurrencyAllows() {
        assertThatThrownBy(() -> Money.of("10.001", "EUR")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-1", "EUR")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAddingDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "EUR").add(Money.of("1", "RON")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timesRoundsHalfEvenToCurrencyScale() {
        // 1.5% of 100.10 = 1.5015 -> 1.50
        assertThat(Money.of("100.10", "RON").times(new BigDecimal("0.015"))).isEqualTo(Money.of("1.50", "RON"));
    }

    @Test
    void minorUnits() {
        assertThat(Money.of("12.34", "EUR").minorUnits()).isEqualTo(1234L);
    }
}
