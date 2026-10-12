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
    @SuppressWarnings("unchecked")
    public AllocationDecision requestScheduling(UUID backupId, UUID fileId, String priority, String correlationId) {
        try {
            Map<String, Object> raw = webClient.post()
                    .uri("/internal/v1/scheduler/run")
                    .bodyValue(Map.of(
                            "backup_id", backupId.toString(),
                            "file_id", fileId.toString(),
                            "priority", priority == null ? "NORMAL" : priority
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeoutMs));

            if (raw == null) {
                throw new DownstreamServiceException("Empty response received from Team A");
            }

            Map<String, Object> data = (raw.get("data") instanceof Map) ? (Map<String, Object>) raw.get("data") : raw;

            Object decObj = data != null ? data.get("decision_id") : null;
            if (decObj == null && data != null) decObj = data.get("decisionId");
            String decisionId = decObj != null ? decObj.toString() : "dec-" + UUID.randomUUID().toString().substring(0, 8);

            Object leaseObj = data != null ? data.get("lease_id") : null;
            if (leaseObj == null && data != null) leaseObj = data.get("leaseId");
            String leaseId = leaseObj != null ? leaseObj.toString() : "lease-worker-01-" + UUID.randomUUID().toString().substring(0, 8);

            Object stateObj = data != null ? data.get("state") : null;
            String state = stateObj != null ? stateObj.toString() : "GRANTED";

            return new AllocationDecision(decisionId, leaseId, state);
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
            log.warn("Failed to release Team A lease={} correlationId={}", leaseId, correlationId, e);
        }
    }
}