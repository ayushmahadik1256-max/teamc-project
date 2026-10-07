package com.apnileap.teamc.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RecoveryVerifyRequest(
        @NotNull UUID fileId,
        UUID versionId,
        Instant targetPointInTime
) {}
