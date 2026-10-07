package com.apnileap.teamc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBackupRequest(
        @NotNull UUID userId,
        @NotNull UUID fileId,
        @NotBlank String backupType,          // FULL / INCREMENTAL
        String priority,                      // NORMAL / HIGH / LOW
        String idempotencyKey
) {}
