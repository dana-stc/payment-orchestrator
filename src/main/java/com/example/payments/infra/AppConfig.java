package com.example.payments.infra;

import com.example.payments.provider.CheapPsp;
import com.example.payments.provider.FastPsp;
import com.example.payments.provider.PaymentProvider;
import com.example.payments.provider.ProviderRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PaymentProvider fastPsp() {
        return new FastPsp();
    }

    @Bean
    PaymentProvider cheapPsp() {
        return new CheapPsp();
    }

    @Bean
    ProviderRouter providerRouter(List<PaymentProvider> providers) {
        return new ProviderRouter(providers);
    }
}
