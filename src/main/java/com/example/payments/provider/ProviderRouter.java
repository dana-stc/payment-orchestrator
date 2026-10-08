package com.example.payments.provider;

import com.example.payments.application.RequestContext;
import com.example.payments.domain.Money;
import com.example.payments.domain.ProviderResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.TimeoutException;

/**
 * Picks the provider and handles fallback.
 * <ol>
 *   <li>Fee quotes are fetched from all supporting providers <b>in parallel</b> (structured
 *       concurrency). Quoting has no side effects, so parallelism is safe.</li>
 *   <li>Providers are then tried <b>one at a time</b>, cheapest first. Authorization has side
 *       effects at the provider, so racing two providers could authorize the same payment twice.</li>
 *   <li>Only {@link ProviderResult#retryableOnAnotherProvider() retryable} outcomes (timeout,
 *       unavailable) move on to the next provider; a decline is final.</li>
 * </ol>
 */
public class ProviderRouter {

    private static final System.Logger log = System.getLogger(ProviderRouter.class.getName());
    private static final Duration QUOTE_TIMEOUT = Duration.ofSeconds(1);

    public record RoutedResult(String provider, ProviderResult result) {
    }

    private record Quote(PaymentProvider provider, Money fee) {
    }

    private final List<PaymentProvider> providers;

    public ProviderRouter(List<PaymentProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public RoutedResult authorize(ProviderRequest request) {
        var ranked = rankByFee(request);
        if (ranked.isEmpty()) {
            return new RoutedResult("none",
                    new ProviderResult.Declined("no provider supports " + request.amount().currency(), "NO_PROVIDER"));
        }

        RoutedResult last = null;
        for (var provider : ranked) {
            var result = callSafely(provider, request);
            last = new RoutedResult(provider.name(), result);
            if (!result.retryableOnAnotherProvider()) {
                return last;
            }
            log.log(System.Logger.Level.WARNING, "[{0}] {1} returned {2}, trying next provider",
                    RequestContext.correlationId(), provider.name(), result);
        }
        return last;
    }

    private List<PaymentProvider> rankByFee(ProviderRequest request) {
        var candidates = providers.stream()
                .filter(p -> p.supports(request.amount().currency()))
                .toList();
        if (candidates.size() < 2) {
            return candidates;
        }

        var quotes = new ArrayList<Quote>();
        try (var scope = new StructuredTaskScope<Optional<Quote>>()) {
            var tasks = candidates.stream()
                    .map(p -> scope.fork(() -> quote(p, request)))
                    .toList();
            try {
                scope.joinUntil(Instant.now().plus(QUOTE_TIMEOUT));
            } catch (TimeoutException e) {
                log.log(System.Logger.Level.WARNING, "[{0}] fee quotes timed out, using those received",
                        RequestContext.correlationId());
            }
            for (Subtask<Optional<Quote>> task : tasks) {
                if (task.state() == Subtask.State.SUCCESS) {
                    task.get().ifPresent(quotes::add);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return candidates;
        }

        var ranked = new ArrayList<>(quotes.stream()
                .sorted(Comparator.comparing((Quote q) -> q.fee().amount()))
                .map(Quote::provider)
                .toList());
        // providers that failed to quote are still usable, just tried last
        candidates.stream().filter(p -> !ranked.contains(p)).forEach(ranked::add);
        return ranked;
    }

    private Optional<Quote> quote(PaymentProvider provider, ProviderRequest request) {
        try {
            return Optional.of(new Quote(provider, provider.quoteFee(request)));
        } catch (RuntimeException e) {
            log.log(System.Logger.Level.WARNING, "[{0}] {1} failed to quote: {2}",
                    RequestContext.correlationId(), provider.name(), e.toString());
            return Optional.empty();
        }
    }

    private ProviderResult callSafely(PaymentProvider provider, ProviderRequest request) {
        try {
            return provider.authorize(request);
        } catch (RuntimeException e) {
            return new ProviderResult.Unavailable(provider.name() + ": " + e.getMessage());
        }
    }
}
