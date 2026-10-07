package com.apnileap.teamc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backups")
@Getter
@Setter
@NoArgsConstructor
public class Backup {
    @Id
    @GeneratedValue
    @Column(name = "backup_id")
    private UUID backupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "backup_type", nullable = false)
    private String backupType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BackupState state;

    @Column(nullable = false)
    private String priority = "NORMAL";

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "lease_id")
    private String leaseId;

    @Column(name = "decision_id")
    private String decisionId;

    @Column(name = "dedup_result_id")
    private String dedupResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
