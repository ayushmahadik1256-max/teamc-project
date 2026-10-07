package com.apnileap.teamc.dto;

import java.time.Instant;
import java.util.UUID;

public record RecoveryVerifyResponse(
        UUID fileId,
        UUID verifiedVersionId,
        boolean consistent,
        long rpoSeconds,
        boolean rpoCompliant,
        long estimatedRtoSeconds,
        boolean rtoCompliant,
        String integrityCheckStatus,
        Instant verifiedAt
) {}
