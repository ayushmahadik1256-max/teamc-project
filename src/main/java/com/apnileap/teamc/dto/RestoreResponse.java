package com.apnileap.teamc.dto;

import com.apnileap.teamc.entity.Restore;

import java.time.Instant;
import java.util.UUID;

public record RestoreResponse(
        UUID restoreId,
        UUID backupId,
        UUID versionId,
        String state,
        String leaseId,
        String correlationId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static RestoreResponse from(Restore r) {
        return new RestoreResponse(
                r.getRestoreId(), r.getBackupId(), r.getVersionId(),
                r.getState() == null ? null : r.getState().name(),
                r.getLeaseId(), r.getCorrelationId(), r.getFailureReason(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
