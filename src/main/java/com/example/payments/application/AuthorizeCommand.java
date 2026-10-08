package com.example.payments.application;

import com.example.payments.domain.Money;

public record AuthorizeCommand(String idempotencyKey, String merchantId, Money amount) {
}
