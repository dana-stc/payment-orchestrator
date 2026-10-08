package com.example.payments.application;

import com.example.payments.domain.PaymentId;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(PaymentId id) {
        super("Payment " + id + " not found");
    }
}
