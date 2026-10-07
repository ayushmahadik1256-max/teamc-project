package com.apnileap.teamc.client;

import com.apnileap.teamc.dto.AllocationDecision;

import java.util.UUID;

public interface TeamAClient {
    AllocationDecision requestScheduling(UUID backupId, UUID fileId, String priority, String correlationId);
    void releaseLease(String leaseId, String correlationId);
}
