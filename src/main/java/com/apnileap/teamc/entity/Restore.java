package com.apnileap.teamc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restores")
@Getter
@Setter
@NoArgsConstructor
public class Restore {
    @Id
    @GeneratedValue
    @Column(name = "restore_id")
    private UUID restoreId;

    @Column(name = "backup_id", nullable = false)
    private UUID backupId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RestoreState state;

    @Column(name = "lease_id")
    private String leaseId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

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
