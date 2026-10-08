package com.example.payments.provider;

import com.example.payments.domain.Money;
import com.example.payments.domain.PaymentId;

public record ProviderRequest(PaymentId paymentId, Money amount, String merchantId) {
}
