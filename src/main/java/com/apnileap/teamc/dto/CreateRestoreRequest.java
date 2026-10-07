package com.apnileap.teamc.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRestoreRequest(
        @NotNull UUID backupId,
        @NotNull UUID userId,
        UUID versionId,                 // optional - defaults to the backup's version if omitted
        String idempotencyKey
) {}
