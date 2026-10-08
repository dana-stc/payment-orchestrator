package com.example.payments.application;

import com.example.payments.domain.Money;
import com.example.payments.domain.PaymentId;
import com.example.payments.domain.PaymentState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a real PostgreSQL (needs Docker). Executed by failsafe: {@code mvn verify}. */
@SpringBootTest
@Testcontainers
class PaymentIdempotencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PaymentService service;

    @Test
    void sameKeySentConcurrentlyCreatesExactlyOnePayment() throws Exception {
        var cmd = new AuthorizeCommand("race-key", "merchant-1", Money.of("100.00", "RON"));

        var ids = new HashSet<PaymentId>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, 10)
                    .mapToObj(i -> executor.submit(() -> service.authorize(cmd)))
                    .toList();
            for (var future : futures) {
                ids.add(future.get().id());
            }
        }

        assertThat(ids).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        service.authorize(new AuthorizeCommand("conflict-key", "merchant-1", Money.of("10.00", "RON")));

        assertThatThrownBy(() ->
                service.authorize(new AuthorizeCommand("conflict-key", "merchant-1", Money.of("11.00", "RON"))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void authorizeThenCaptureHappyPath() {
        var authorized = service.authorize(new AuthorizeCommand("happy-key", "merchant-1", Money.of("50.00", "EUR")));
        assertThat(authorized.state()).isEqualTo(PaymentState.AUTHORIZED);

        var captured = service.capture(authorized.id());
        assertThat(captured.state()).isEqualTo(PaymentState.CAPTURED);
        assertThat(service.get(authorized.id()).history()).hasSize(3);
    }
}
