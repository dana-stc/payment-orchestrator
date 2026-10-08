package com.example.payments.provider;

import com.example.payments.domain.Money;
import com.example.payments.domain.PaymentId;
import com.example.payments.domain.ProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRouterTest {

    private final ProviderRouter router = new ProviderRouter(List.of(new FastPsp(), new CheapPsp()));

    private ProviderRequest request(String amount, String currency) {
        return new ProviderRequest(PaymentId.random(), Money.of(amount, currency), "m1");
    }

    @Test
    void picksCheapestProviderFirst() {
        var routed = router.authorize(request("100.00", "RON"));
        assertThat(routed.provider()).isEqualTo("CheapPsp");
        assertThat(routed.result()).isInstanceOf(ProviderResult.Approved.class);
    }

    @Test
    void onlyProviderSupportingTheCurrencyIsUsed() {
        var routed = router.authorize(request("100.00", "USD"));
        assertThat(routed.provider()).isEqualTo("FastPsp");
    }

    @Test
    void declineIsFinalAndDoesNotFallBack() {
        // CheapPsp declines >= 5000; FastPsp would approve, but a decline must not be retried elsewhere
        var routed = router.authorize(request("6000.00", "RON"));
        assertThat(routed.provider()).isEqualTo("CheapPsp");
        assertThat(routed.result()).isInstanceOf(ProviderResult.Declined.class);
    }

    @Test
    void fallsBackWhenFirstProviderTimesOut() {
        PaymentProvider timingOutCheap = new CheapPsp() {
            @Override
            public ProviderResult authorize(ProviderRequest request) {
                return new ProviderResult.Timeout(java.time.Duration.ofSeconds(2));
            }
        };
        var routed = new ProviderRouter(List.of(new FastPsp(), timingOutCheap)).authorize(request("100.00", "RON"));
        assertThat(routed.provider()).isEqualTo("FastPsp");
        assertThat(routed.result()).isInstanceOf(ProviderResult.Approved.class);
    }

    @Test
    void providerThatThrowsIsTreatedAsUnavailableAndSkipped() {
        PaymentProvider broken = new CheapPsp() {
            @Override
            public ProviderResult authorize(ProviderRequest request) {
                throw new IllegalStateException("boom");
            }
        };
        var routed = new ProviderRouter(List.of(new FastPsp(), broken)).authorize(request("100.00", "RON"));
        assertThat(routed.provider()).isEqualTo("FastPsp");
    }

    @Test
    void providerFailingToQuoteIsStillTriedLast() {
        PaymentProvider noQuote = new CheapPsp() {
            @Override
            public Money quoteFee(ProviderRequest request) {
                throw new IllegalStateException("no quote");
            }
        };
        var routed = new ProviderRouter(List.of(new FastPsp(), noQuote)).authorize(request("100.00", "RON"));
        assertThat(routed.provider()).isEqualTo("FastPsp");
    }

    @Test
    void allProvidersTimingOutReturnsLastTimeout() {
        // 100.13 -> FastPsp times out; make CheapPsp time out too
        PaymentProvider timingOutCheap = new CheapPsp() {
            @Override
            public ProviderResult authorize(ProviderRequest request) {
                return new ProviderResult.Unavailable("down");
            }
        };
        var routed = new ProviderRouter(List.of(new FastPsp(), timingOutCheap)).authorize(request("100.13", "RON"));
        assertThat(routed.result().retryableOnAnotherProvider()).isTrue();
    }

    @Test
    void unsupportedCurrencyIsDeclined() {
        var routed = router.authorize(request("100", "JPY"));
        assertThat(routed.provider()).isEqualTo("none");
        assertThat(routed.result()).isInstanceOf(ProviderResult.Declined.class);
    }
}
