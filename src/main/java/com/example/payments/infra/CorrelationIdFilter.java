package com.example.payments.infra;

import com.example.payments.application.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Binds the correlation id (from X-Correlation-Id, or generated) as a ScopedValue for the whole request. */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader(HEADER);
        var correlationId = (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
        response.setHeader(HEADER, correlationId);

        // ScopedValue.run takes a Runnable, so carry checked exceptions out of the lambda
        var failure = new AtomicReference<Exception>();
        ScopedValue.where(RequestContext.CORRELATION_ID, correlationId).run(() -> {
            try {
                chain.doFilter(request, response);
            } catch (Exception e) {
                failure.set(e);
            }
        });

        switch (failure.get()) {
            case null -> { }
            case IOException e -> throw e;
            case ServletException e -> throw e;
            case RuntimeException e -> throw e;
            default -> throw new IllegalStateException(failure.get());
        }
    }
}
