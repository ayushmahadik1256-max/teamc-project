package com.apnileap.teamc.entity;

public enum BackupState {
    REQUESTED,
    QUEUED,
    CHUNKING,
    DEDUPLICATING,
    UPLOADING,
    COMMITTED,
    VERIFIED,
    COMPLETED,
    REJECTED,
    PARTIAL,
    RETRYING,
    CANCELLED,
    EXPIRED,
    COMPENSATION_REQUIRED
}
