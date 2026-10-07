package com.apnileap.teamc.client;

import com.apnileap.teamc.dto.DedupResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "teamc.integration.use-mock-clients", havingValue = "true", matchIfMissing = true)
public class MockTeamBClient implements TeamBClient {
    @Override
    public DedupResult computeDedup(UUID fileId, String correlationId) {
        log.info("[MOCK Team B] computing dedup for fileId={} correlationId={}", fileId, correlationId);
        String dedupResultId = "dedup-" + UUID.randomUUID();
        String merkleRoot = sha256(fileId.toString() + correlationId);
        List<String> chunkRefs = List.of(
                "chk-" + UUID.randomUUID().toString().substring(0, 8),
                "chk-" + UUID.randomUUID().toString().substring(0, 8)
        );
        return new DedupResult(dedupResultId, 8, 2, 0.20, merkleRoot, chunkRefs);
    }

    @Override
    public boolean verifyIntegrity(UUID fileId, String merkleRoot, String correlationId) {
        log.info("[MOCK Team B] verifying integrity fileId={} correlationId={}", fileId, correlationId);
        return merkleRoot != null && !merkleRoot.isBlank();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
