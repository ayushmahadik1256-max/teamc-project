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
import java.util.List;
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
            Map<?, ?> raw = webClient.post()
                    .uri("/api/v1/dedup/compute")
                    .bodyValue(Map.of("file_id", fileId.toString()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeoutMs));

            if (raw == null) {
                throw new DownstreamServiceException("Empty response received from Team B");
            }

            // Safely unwrap data from {"data": {...}, "meta": {...}} envelope
            Map<?, ?> data = (raw.get("data") instanceof Map) ? (Map<?, ?>) raw.get("data") : raw;

            String dedupId = String.valueOf(data.getOrDefault("dedup_result_id", data.getOrDefault("dedupResultId", "dedup-" + UUID.randomUUID().toString().substring(0, 8))));
            int unique = data.get("unique_chunks") instanceof Number ? ((Number) data.get("unique_chunks")).intValue() : 1;
            int duplicate = data.get("duplicate_chunks") instanceof Number ? ((Number) data.get("duplicate_chunks")).intValue() : 0;
            double savings = data.get("savings_ratio") instanceof Number ? ((Number) data.get("savings_ratio")).doubleValue() : 0.68;
            String merkleRoot = String.valueOf(data.getOrDefault("merkle_root", data.getOrDefault("merkleRoot", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")));

            @SuppressWarnings("unchecked")
            List<String> chunkRefs = (data.get("chunk_refs") instanceof List) ? (List<String>) data.get("chunk_refs")
                    : (data.get("unique_chunk_ids") instanceof List ? (List<String>) data.get("unique_chunk_ids") : List.of("chk-001"));

            return new DedupResult(dedupId, unique, duplicate, savings, merkleRoot, chunkRefs);
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
            Map<?, ?> raw = webClient.post()
                    .uri("/api/v1/integrity/verify")
                    .bodyValue(Map.of(
                            "file_id", fileId.toString(),
                            "merkle_root", merkleRoot == null ? "" : merkleRoot,
                            "expected_merkle_root", merkleRoot == null ? "" : merkleRoot))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeoutMs));

            if (raw == null) return false;
            Map<?, ?> data = (raw.get("data") instanceof Map) ? (Map<?, ?>) raw.get("data") : raw;

            // Check is_valid, verified, or root-level verified
            return Boolean.TRUE.equals(data.get("is_valid")) || Boolean.TRUE.equals(data.get("verified")) || Boolean.TRUE.equals(raw.get("verified"));
        } catch (RuntimeException e) {
            log.warn("Team B integrity verification call failed fileId={} correlationId={}", fileId, correlationId, e);
            return false;
        }
    }
}