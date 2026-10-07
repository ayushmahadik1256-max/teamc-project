package com.apnileap.teamc.client;

import com.apnileap.teamc.dto.AllocationDecision;
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
public class RealTeamAClient implements TeamAClient {
    private final WebClient webClient;
    private final long timeoutMs;

    public RealTeamAClient(@Qualifier("teamAWebClient") WebClient webClient,
                            @Value("${teamc.integration.call-timeout-ms}") long timeoutMs) {
        this.webClient = webClient;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public AllocationDecision requestScheduling(UUID backupId, UUID fileId, String priority, String correlationId) {
        try {
            return webClient.post()
                    .uri("/internal/v1/scheduler/run")
                    .bodyValue(Map.of(
                            "backup_id", backupId.toString(),
                            "file_id", fileId.toString(),
                            "priority", priority == null ? "NORMAL" : priority
                    ))
                    .retrieve()
                    .bodyToMono(AllocationDecision.class)
                    .block(Duration.ofMillis(timeoutMs));
        } catch (WebClientResponseException e) {
            log.error("Team A scheduling call failed: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new DownstreamServiceException("Team A scheduling request failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Team A scheduling call failed unexpectedly", e);
            throw new DownstreamServiceException("Team A scheduling request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void releaseLease(String leaseId, String correlationId) {
        try {
            webClient.delete()
                    .uri("/internal/v1/locks/{leaseId}", leaseId)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(timeoutMs));
            log.info("Released Team A lease={} correlationId={}", leaseId, correlationId);
        } catch (RuntimeException e) {
            log.error("Failed to release Team A lease={} correlationId={}", leaseId, correlationId, e);
        }
    }
}
