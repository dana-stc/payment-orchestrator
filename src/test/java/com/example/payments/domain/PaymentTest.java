package com.example.payments.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");

    private Payment newPayment() {
        return Payment.create("key-1", "merchant-1", Money.of("100.00", "RON"), NOW);
    }

    @Test
    void startsInCreatedWithOneHistoryEntry() {
        var p = newPayment();
        assertThat(p.state()).isEqualTo(PaymentState.CREATED);
        assertThat(p.history()).hasSize(1);
    }

    @Test
    void approvedResultAuthorizesAndStoresProviderReference() {
        var p = newPayment().applyProviderResult("FastPsp", new ProviderResult.Approved("ref-1", NOW), NOW);
        assertThat(p.state()).isEqualTo(PaymentState.AUTHORIZED);
        assertThat(p.provider()).isEqualTo("FastPsp");
        assertThat(p.providerRef()).isEqualTo("ref-1");
    }

    @Test
    void declinedResultFailsWithReasonInHistory() {
        var p = newPayment().applyProviderResult("FastPsp", new ProviderResult.Declined("no funds", "51"), NOW);
        assertThat(p.state()).isEqualTo(PaymentState.FAILED);
        assertThat(p.lastTransition().reason()).contains("no funds");
    }

    @Test
    void timeoutFailsPayment() {
        var p = newPayment().applyProviderResult("FastPsp", new ProviderResult.Timeout(Duration.ofSeconds(2)), NOW);
        assertThat(p.state()).isEqualTo(PaymentState.FAILED);
    }

    @Test
    void cannotCaptureBeforeAuthorization() {
        assertThatThrownBy(() -> newPayment().transitionTo(PaymentState.CAPTURED, "x", NOW))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cannotLeaveTerminalState() {
        var failed = newPayment().transitionTo(PaymentState.FAILED, "x", NOW);
        assertThatThrownBy(() -> failed.transitionTo(PaymentState.AUTHORIZED, "x", NOW))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void historyIsKeptInOrderAndLastIsMostRecent() {
        var p = newPayment()
                .transitionTo(PaymentState.AUTHORIZED, "auth", NOW)
                .transitionTo(PaymentState.CAPTURED, "capture", NOW);
        assertThat(p.lastTransition().to()).isEqualTo(PaymentState.CAPTURED);
        assertThat(p.history().reversed()).extracting(StateTransition::to)
                .containsExactly(PaymentState.CAPTURED, PaymentState.AUTHORIZED, PaymentState.CREATED);
    }

    @Test
    void originalIsNotMutatedByTransition() {
        var original = newPayment();
        original.transitionTo(PaymentState.AUTHORIZED, "auth", NOW);
        assertThat(original.state()).isEqualTo(PaymentState.CREATED);
    }
}
