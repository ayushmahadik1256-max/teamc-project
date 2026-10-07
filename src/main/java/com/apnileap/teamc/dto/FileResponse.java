package com.apnileap.teamc.dto;

import com.apnileap.teamc.entity.FileEntity;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID fileId,
        UUID userId,
        String fileName,
        String sourcePath,
        String retentionPolicy,
        String storageLocation,
        UUID currentVersionId,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileResponse from(FileEntity f) {
        return new FileResponse(
                f.getFileId(), f.getUserId(), f.getFileName(), f.getSourcePath(),
                f.getRetentionPolicy(), f.getStorageLocation(), f.getCurrentVersionId(),
                f.getCreatedAt(), f.getUpdatedAt()
        );
    }
}
