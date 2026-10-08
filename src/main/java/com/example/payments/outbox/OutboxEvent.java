package com.example.payments.outbox;

import com.example.payments.domain.PaymentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Row in the outbox table. It is written in the same DB transaction as the state change, so
 * the event exists if and only if the change was committed. A separate publisher ships it.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent of(PaymentEvent event, ObjectMapper mapper, Instant now) {
        var row = new OutboxEvent();
        row.id = UUID.randomUUID();
        row.aggregateId = event.paymentId().value();
        row.type = event.type();
        row.createdAt = now;
        row.payload = toJson(event, mapper);
        return row;
    }

    private static String toJson(PaymentEvent event, ObjectMapper mapper) {
        var body = new LinkedHashMap<String, Object>();
        body.put("paymentId", event.paymentId().toString());
        body.put("amount", event.amount().amount().toPlainString());
        body.put("currency", event.amount().currency().getCurrencyCode());
        // record pattern: pull the extra field out of the one variant that has it
        if (event instanceof PaymentEvent.Failed(var id, var amount, var reason)) {
            body.put("reason", reason);
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize event " + event.type(), e);
        }
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }
}
