package com.example.payments.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Polls the outbox and publishes pending events.
 * TODO: replace the log line with a KafkaTemplate send, and use
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} before running more than one instance.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        for (var event : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAt()) {
            log.info("publishing {} {}", event.getType(), event.getPayload());
            event.markPublished(clock.instant());
        }
    }
}
