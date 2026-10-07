package com.apnileap.teamc.dto;

import com.apnileap.teamc.entity.Backup;

import java.time.Instant;
import java.util.UUID;

public record BackupResponse(
        UUID backupId,
        UUID userId,
        UUID fileId,
        UUID versionId,
        String backupType,
        String state,
        String verificationStatus,
        String leaseId,
        String dedupResultId,
        String correlationId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static BackupResponse from(Backup b) {
        return new BackupResponse(
                b.getBackupId(), b.getUserId(), b.getFileId(), b.getVersionId(),
                b.getBackupType(),
                b.getState() == null ? null : b.getState().name(),
                b.getVerificationStatus() == null ? null : b.getVerificationStatus().name(),
                b.getLeaseId(), b.getDedupResultId(), b.getCorrelationId(), b.getFailureReason(),
                b.getCreatedAt(), b.getUpdatedAt()
        );
    }
}
