package com.apnileap.teamc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateFileRequest(
        @NotNull UUID userId,
        @NotBlank String fileName,
        @NotBlank String sourcePath,
        String retentionPolicy
) {}
