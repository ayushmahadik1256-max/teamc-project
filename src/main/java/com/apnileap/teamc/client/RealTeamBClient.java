package com.apnileap.teamc.client;

import com.apnileap.teamc.dto.DedupResult;
import com.apnileap.teamc.exception.DownstreamServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "teamc.integration.use-mock-clients", havingValue = "false")
public class RealTeamBClient implements TeamBClient {
    private final WebClient webClient;
    private final long timeoutMs;

    public RealTeamBClient(@Qualifier("teamBWebClient") WebClient webClient,
                            @Value("${teamc.integration.call-timeout-ms}") long timeoutMs) {
        this.webClient = webClient;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public DedupResult computeDedup(UUID fileId, String correlationId) {
        try {
            return webClient.post()
                    .uri("/api/v1/dedup/compute")
                    .bodyValue(Map.of("file_id", fileId.toString()))
                    .retrieve()
                    .bodyToMono(DedupResult.class)
                    .block(Duration.ofMillis(timeoutMs));
        } catch (WebClientResponseException e) {
            log.error("Team B dedup call failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new DownstreamServiceException("Team B dedup request failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Team B dedup call failed unexpectedly", e);
            throw new DownstreamServiceException("Team B dedup request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyIntegrity(UUID fileId, String merkleRoot, String correlationId) {
        try {
            Map<?, ?> result = webClient.post()
                    .uri("/api/v1/integrity/verify")
                    .bodyValue(Map.of(
                            "file_id", fileId.toString(),
                            "merkle_root", merkleRoot == null ? "" : merkleRoot))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeoutMs));
            return result != null && Boolean.TRUE.equals(result.get("verified"));
        } catch (RuntimeException e) {
            log.error("Team B integrity verification failed fileId={} correlationId={}",
                    fileId, correlationId, e);
            return false;
        }
    }
}
