package com.example.payments.application;

import com.example.payments.domain.Payment;
import com.example.payments.domain.PaymentEvent;
import com.example.payments.domain.PaymentId;
import com.example.payments.domain.PaymentState;
import com.example.payments.outbox.OutboxEvent;
import com.example.payments.outbox.OutboxRepository;
import com.example.payments.persistence.PaymentEntity;
import com.example.payments.persistence.PaymentRepository;
import com.example.payments.provider.ProviderRequest;
import com.example.payments.provider.ProviderRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final ProviderRouter router;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ObjectMapper mapper;

    public PaymentService(PaymentRepository payments, OutboxRepository outbox, ProviderRouter router,
                          TransactionTemplate tx, Clock clock, ObjectMapper mapper) {
        this.payments = payments;
        this.outbox = outbox;
        this.router = router;
        this.tx = tx;
        this.clock = clock;
        this.mapper = mapper;
    }

    /**
     * Three steps on purpose, so no DB connection is held while waiting for a provider:
     * (1) insert the payment as CREATED, relying on the unique idempotency_key constraint,
     * (2) call the provider outside any transaction (blocking is cheap on virtual threads),
     * (3) record the result and the outbox event in one transaction.
     *
     * TODO: a crash between (1) and (3) leaves a payment in CREATED; add a recovery job.
     */
    public Payment authorize(AuthorizeCommand cmd) {
        var existing = payments.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get().toDomain(), cmd);
        }

        Payment created;
        try {
            created = tx.execute(status -> payments.saveAndFlush(PaymentEntity.from(
                    Payment.create(cmd.idempotencyKey(), cmd.merchantId(), cmd.amount(), clock.instant()))).toDomain());
        } catch (DataIntegrityViolationException lostRace) {
            // another request with the same key committed first: return its payment
            var winner = payments.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow(() -> lostRace);
            return replay(winner.toDomain(), cmd);
        }
        log.info("[{}] payment {} created", RequestContext.correlationId(), created.id());

        var routed = router.authorize(new ProviderRequest(created.id(), created.amount(), created.merchantId()));

        return tx.execute(status -> {
            var entity = load(created.id());
            var updated = entity.toDomain().applyProviderResult(routed.provider(), routed.result(), clock.instant());
            entity.apply(updated);
            outbox.save(OutboxEvent.of(eventFor(updated), mapper, clock.instant()));
            log.info("[{}] payment {} -> {}", RequestContext.correlationId(), updated.id(), updated.state());
            return updated;
        });
    }

    public Payment capture(PaymentId id) {
        return tx.execute(status -> {
            var entity = load(id);
            var captured = entity.toDomain().transitionTo(PaymentState.CAPTURED, "captured", clock.instant());
            entity.apply(captured);
            outbox.save(OutboxEvent.of(eventFor(captured), mapper, clock.instant()));
            return captured;
        });
    }

    public Payment get(PaymentId id) {
        return load(id).toDomain();
    }

    private PaymentEntity load(PaymentId id) {
        return payments.findById(id.value()).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private Payment replay(Payment existing, AuthorizeCommand cmd) {
        boolean sameRequest = existing.merchantId().equals(cmd.merchantId()) && existing.amount().equals(cmd.amount());
        if (!sameRequest) {
            throw new IdempotencyConflictException(cmd.idempotencyKey());
        }
        return existing;
    }

    private PaymentEvent eventFor(Payment p) {
        return switch (p.state()) {
            case AUTHORIZED -> new PaymentEvent.Authorized(p.id(), p.amount());
            case CAPTURED -> new PaymentEvent.Captured(p.id(), p.amount());
            case FAILED -> new PaymentEvent.Failed(p.id(), p.amount(), p.lastTransition().reason());
            default -> throw new IllegalStateException("no event defined for state " + p.state());
        };
    }
}
