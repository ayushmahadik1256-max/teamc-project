package com.apnileap.teamc.client;

import com.apnileap.teamc.dto.DedupResult;

import java.util.UUID;

public interface TeamBClient {
    DedupResult computeDedup(UUID fileId, String correlationId);
    boolean verifyIntegrity(UUID fileId, String merkleRoot, String correlationId);
}
