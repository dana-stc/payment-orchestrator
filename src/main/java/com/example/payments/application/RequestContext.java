package com.example.payments.application;

/**
 * Per-request context carried in a {@link ScopedValue}: immutable, cheap on virtual threads,
 * and automatically inherited by subtasks forked in a {@code StructuredTaskScope}.
 */
public final class RequestContext {

    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    private RequestContext() {
    }

    public static String correlationId() {
        return CORRELATION_ID.orElse("no-correlation-id");
    }
}
