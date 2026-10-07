package com.apnileap.teamc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private static ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> {
            String correlationId = CorrelationContext.current();
            if (correlationId != null && !correlationId.isBlank()) {
                return next.exchange(
                        org.springframework.web.reactive.function.client.ClientRequest
                                .from(request)
                                .header(CorrelationContext.HEADER, correlationId)
                                .build());
            }
            return next.exchange(request);
        };
    }

    @Bean("teamAWebClient")
    public WebClient teamAWebClient(
            @org.springframework.beans.factory.annotation.Value("${teamc.integration.team-a-base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .filter(correlationIdFilter())
                .build();
    }

    @Bean("teamBWebClient")
    public WebClient teamBWebClient(
            @org.springframework.beans.factory.annotation.Value("${teamc.integration.team-b-base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .filter(correlationIdFilter())
                .build();
    }
}
