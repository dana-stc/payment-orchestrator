package com.example.payments.provider;

import com.example.payments.domain.Money;
import com.example.payments.domain.ProviderResult;

import java.util.Currency;

/** Port towards an external payment provider (PSP / acquirer). Calls are blocking by design. */
public interface PaymentProvider {

    String name();

    boolean supports(Currency currency);

    /** Fee this provider would charge for the payment. Side-effect free, so safe to call in parallel. */
    Money quoteFee(ProviderRequest request);

    /** Has side effects at the provider: must be called for at most one provider at a time. */
    ProviderResult authorize(ProviderRequest request);
}
