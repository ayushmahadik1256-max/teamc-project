package com.apnileap.teamc.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record AllocationDecision(
        @JsonAlias("decision_id") String decisionId,
        @JsonAlias("lease_id") String leaseId,
        String state
) {}
