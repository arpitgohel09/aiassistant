package com.projectAI.aiassistant.tools;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ExchangeRateService {

    private final RestClient client;

    public ExchangeRateService() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl("https://api.frankfurter.dev").requestFactory(factory).build();
    }

    public Rate rate(String base, String target) {
        String from = currency(base);
        String to = currency(target);
        if (from.equals(to)) {
            throw new IllegalArgumentException("Choose two different currencies");
        }
        // Keep the host fixed; callers supply currency codes, never a URL.
        Rate result = client.get()
            .uri("/v1/latest?base={base}&symbols={target}", from, to)
            .retrieve()
            .body(Rate.class);
        if (result == null || result.rates() == null || !result.rates().containsKey(to)) {
            throw new IllegalStateException("Exchange provider returned no rate");
        }
        return result;
    }

    private String currency(String value) {
        if (value == null || !value.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency must contain three letters");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public record Rate(String base, String date, Map<String, BigDecimal> rates) {}
}
