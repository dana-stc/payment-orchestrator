package com.example.payments.persistence;

import com.example.payments.domain.Money;
import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentId;
import com.example.payments.domain.PaymentState;
import com.example.payments.domain.StateTransition;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** JPA mapping of {@link Payment}. The domain model stays free of persistence annotations. */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentState state;

    private String provider;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Optimistic lock: two concurrent captures cannot both succeed. Null means "new" to Spring Data. */
    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "payment_transitions", joinColumns = @JoinColumn(name = "payment_id"))
    @OrderColumn(name = "position")
    private List<TransitionRow> history = new ArrayList<>();

    protected PaymentEntity() {
    }

    public static PaymentEntity from(Payment payment) {
        var entity = new PaymentEntity();
        entity.id = payment.id().value();
        entity.idempotencyKey = payment.idempotencyKey();
        entity.merchantId = payment.merchantId();
        entity.amount = payment.amount().amount();
        entity.currency = payment.amount().currency().getCurrencyCode();
        entity.createdAt = payment.history().getFirst().at();
        entity.apply(payment);
        return entity;
    }

    /** Copies the mutable parts of the domain object (state, provider, history) into this entity. */
    public void apply(Payment payment) {
        this.state = payment.state();
        this.provider = payment.provider();
        this.providerRef = payment.providerRef();
        this.history.clear();
        payment.history().forEach(t -> this.history.add(TransitionRow.from(t)));
    }

    public Payment toDomain() {
        return new Payment(
                new PaymentId(id),
                idempotencyKey,
                merchantId,
                new Money(amount, Currency.getInstance(currency)),
                state,
                provider,
                providerRef,
                history.stream().map(TransitionRow::toDomain).toList());
    }

    @Embeddable
    public static class TransitionRow {

        @Enumerated(EnumType.STRING)
        @Column(name = "from_state")
        private PaymentState fromState;

        @Enumerated(EnumType.STRING)
        @Column(name = "to_state", nullable = false)
        private PaymentState toState;

        private String reason;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected TransitionRow() {
        }

        static TransitionRow from(StateTransition t) {
            var row = new TransitionRow();
            row.fromState = t.from();
            row.toState = t.to();
            row.reason = t.reason();
            row.occurredAt = t.at();
            return row;
        }

        StateTransition toDomain() {
            return new StateTransition(fromState, toState, reason, occurredAt);
        }
    }
}
