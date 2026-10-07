package com.apnileap.teamc.client;

import com.apnileap.teamc.dto.AllocationDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "teamc.integration.use-mock-clients", havingValue = "true", matchIfMissing = true)
public class MockTeamAClient implements TeamAClient {
    @Override
    public AllocationDecision requestScheduling(UUID backupId, UUID fileId, String priority, String correlationId) {
        log.info("[MOCK Team A] scheduling requested backupId={} fileId={} priority={} correlationId={}",
                backupId, fileId, priority, correlationId);
        String decisionId = "decision-" + UUID.randomUUID();
        String leaseId = "lease-" + UUID.randomUUID();
        return new AllocationDecision(decisionId, leaseId, "GRANTED");
    }

    @Override
    public void releaseLease(String leaseId, String correlationId) {
        log.info("[MOCK Team A] releasing lease={} correlationId={}", leaseId, correlationId);
    }
}
