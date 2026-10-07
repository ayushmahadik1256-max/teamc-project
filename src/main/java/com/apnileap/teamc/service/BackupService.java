package com.apnileap.teamc.service;

import com.apnileap.teamc.client.TeamAClient;
import com.apnileap.teamc.client.TeamBClient;
import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.AllocationDecision;
import com.apnileap.teamc.dto.BackupResponse;
import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.dto.DedupResult;
import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.entity.Backup;
import com.apnileap.teamc.entity.BackupState;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.FileVersion;
import com.apnileap.teamc.entity.VerificationStatus;
import com.apnileap.teamc.exception.DownstreamServiceException;
import com.apnileap.teamc.exception.IdempotencyConflictException;
import com.apnileap.teamc.exception.ResourceConflictException;
import com.apnileap.teamc.exception.ResourceNotFoundException;
import com.apnileap.teamc.repository.AuditLogRepository;
import com.apnileap.teamc.repository.BackupRepository;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.FileVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class BackupService {
    private final BackupRepository backupRepository;
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final TeamAClient teamAClient;
    private final TeamBClient teamBClient;
    private final EventStreamService eventStreamService;
    private final TransactionTemplate requiresNewTx;
    private final TransactionTemplate standardTx;

    public BackupService(BackupRepository backupRepository,
                          FileRepository fileRepository,
                          FileVersionRepository fileVersionRepository,
                          AuditLogRepository auditLogRepository,
                          TeamAClient teamAClient,
                          TeamBClient teamBClient,
                          EventStreamService eventStreamService,
                          PlatformTransactionManager txManager) {
        this.backupRepository = backupRepository;
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.auditLogRepository = auditLogRepository;
        this.teamAClient = teamAClient;
        this.teamBClient = teamBClient;
        this.eventStreamService = eventStreamService;

        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.standardTx = new TransactionTemplate(txManager);
    }

    public BackupResponse createBackup(CreateBackupRequest request, String correlationId) {
        String corr = safe(correlationId);

        // 1. Fast-path idempotency lookup
        Optional<Backup> existing = backupRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={} -> returning existing backupId={}",
                    request.idempotencyKey(), existing.get().getBackupId());
            return BackupResponse.from(existing.get());
        }

        FileEntity file = fileRepository.findById(request.fileId()
                ).orElseThrow(() -> new ResourceNotFoundException("File not found: " + request.fileId()));

        // 2. Persist initial REQUESTED state in an autonomous transaction
        Backup backup;
        try {
            backup = requiresNewTx.execute(status -> {
                Optional<Backup> already = backupRepository.findByIdempotencyKey(request.idempotencyKey());
                if (already.isPresent()) {
                    return already.get();
                }
                Backup b = new Backup();
                b.setUserId(request.userId());
                b.setFileId(request.fileId());
                b.setBackupType(request.backupType());
                b.setPriority(request.priority() == null ? "NORMAL" : request.priority());
                b.setState(BackupState.REQUESTED);
                b.setIdempotencyKey(request.idempotencyKey());
                b.setCorrelationId(corr);
                b.setVerificationStatus(VerificationStatus.PENDING);
                return backupRepository.saveAndFlush(b);
            });
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotency race on key={} - reading winner", request.idempotencyKey());
            Backup winner = backupRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "Concurrent insert for idempotency_key=" + request.idempotencyKey()
                                    + " could not be resolved."));
            return BackupResponse.from(winner);
        }

        if (backup == null) {
            throw new IdempotencyConflictException("Failed to initialize backup record for key=" + request.idempotencyKey());
        }

        if (backup.getState() == BackupState.COMPLETED || backup.getState() == BackupState.VERIFIED) {
            return BackupResponse.from(backup);
        }

        final UUID backupId = backup.getBackupId();
        eventStreamService.publishEvent("BACKUP_REQUESTED", Map.of("backupId", backupId, "fileId", file.getFileId()));

        try {
            // 3. Request scheduling & worker lease from Team A (State: QUEUED)
            AllocationDecision decision = teamAClient.requestScheduling(
                    backupId, file.getFileId(), backup.getPriority(), corr);

            requiresNewTx.executeWithoutResult(status -> {
                Backup b = backupRepository.findById(backupId).orElseThrow();
                b.setState(BackupState.QUEUED);
                b.setDecisionId(decision.decisionId());
                b.setLeaseId(decision.leaseId());
                backupRepository.saveAndFlush(b);
            });
            backup.setDecisionId(decision.decisionId());
            backup.setLeaseId(decision.leaseId());
            eventStreamService.publishEvent("BACKUP_QUEUED", Map.of("backupId", backupId, "leaseId", decision.leaseId()));

            // 4 & 5. Chunking & deduplication via Team B (State: CHUNKING -> DEDUPLICATING)
            requiresNewTx.executeWithoutResult(status -> {
                Backup b = backupRepository.findById(backupId).orElseThrow();
                b.setState(BackupState.CHUNKING);
                backupRepository.saveAndFlush(b);
            });
            eventStreamService.publishEvent("BACKUP_CHUNKING", Map.of("backupId", backupId));

            DedupResult dedup = teamBClient.computeDedup(file.getFileId(), corr);

            requiresNewTx.executeWithoutResult(status -> {
                Backup b = backupRepository.findById(backupId).orElseThrow();
                b.setDedupResultId(dedup.dedupResultId());
                b.setState(BackupState.DEDUPLICATING);
                backupRepository.saveAndFlush(b);
            });
            backup.setDedupResultId(dedup.dedupResultId());
            eventStreamService.publishEvent("BACKUP_DEDUPLICATING", Map.of("backupId", backupId, "dedupResultId", dedup.dedupResultId()));

            // Transition through UPLOADING state per design state machine
            requiresNewTx.executeWithoutResult(status -> {
                Backup b = backupRepository.findById(backupId).orElseThrow();
                b.setState(BackupState.UPLOADING);
                backupRepository.saveAndFlush(b);
            });
            backup.setState(BackupState.UPLOADING);

            // 6 & 7. Single atomic commit: FileVersion + ChunkRefs + Backup COMMITTED + File pointer
            standardTx.executeWithoutResult(status -> {
                int nextVersionNumber = fileVersionRepository
                        .findTopByFileIdOrderByVersionNumberDesc(file.getFileId())
                        .map(v -> v.getVersionNumber() + 1)
                        .orElse(1);

                FileVersion version = new FileVersion();
                version.setFileId(file.getFileId());
                version.setVersionNumber(nextVersionNumber);
                version.setChecksum(dedup.merkleRoot() == null ? "" : dedup.merkleRoot());
                version.setMerkleRoot(dedup.merkleRoot());
                version.setChunkRefs(dedup.chunkRefs());
                version.setDedupResultId(dedup.dedupResultId());
                version.setState("COMMITTED");
                version.setRetentionPolicy(file.getRetentionPolicy());
                version = fileVersionRepository.save(version);

                Backup b = backupRepository.findById(backupId).orElseThrow();
                b.setVersionId(version.getVersionId());
                b.setState(BackupState.COMMITTED);
                backupRepository.save(b);

                file.setCurrentVersionId(version.getVersionId());
                fileRepository.save(file);

                backup.setVersionId(version.getVersionId());
                backup.setState(BackupState.COMMITTED);
            });
            eventStreamService.publishEvent("BACKUP_COMMITTED", Map.of("backupId", backupId, "versionId", backup.getVersionId()));

            // 8. Integrity verification with graceful degradation
            boolean verified = teamBClient.verifyIntegrity(file.getFileId(), dedup.merkleRoot(), corr);

            standardTx.executeWithoutResult(status -> {
                Backup b = backupRepository.findById(backupId).orElseThrow();
                if (verified) {
                    b.setState(BackupState.COMPLETED);
                    b.setVerificationStatus(VerificationStatus.VERIFIED);
                } else {
                    b.setState(BackupState.COMPLETED);
                    b.setVerificationStatus(VerificationStatus.DEGRADED);
                }
                backupRepository.save(b);

                backup.setState(b.getState());
                backup.setVerificationStatus(b.getVerificationStatus());

                writeAudit(request.userId().toString(), "BACKUP_COMMIT", "BACKUP",
                        backupId.toString(), corr, snapshot(b),
                        verified ? Map.of("verification_status", "VERIFIED") : Map.of("verification_status", "DEGRADED"));
            });

            eventStreamService.publishEvent("BACKUP_COMPLETED", BackupResponse.from(backup));
            return BackupResponse.from(backup);

        } catch (DownstreamServiceException e) {
            log.error("Backup workflow failed for backupId={}, compensating.", backupId, e);
            recordCompensation(backupId, backup.getLeaseId(), e.getMessage(), request.userId().toString(), corr);
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected failure for backupId={}, compensating.", backupId, e);
            recordCompensation(backupId, backup.getLeaseId(), e.getMessage(), request.userId().toString(), corr);
            throw e;
        }
    }

    private void recordCompensation(UUID backupId, String leaseId, String reason, String actor, String correlationId) {
        if (leaseId != null) {
            try {
                teamAClient.releaseLease(leaseId, correlationId);
            } catch (RuntimeException ex) {
                log.warn("Lease release threw during compensation: {}", ex.getMessage());
            }
        }

        requiresNewTx.executeWithoutResult(status -> {
            Backup b = backupRepository.findById(backupId).orElse(null);
            if (b != null) {
                b.setState(BackupState.COMPENSATION_REQUIRED);
                b.setFailureReason(reason);
                backupRepository.saveAndFlush(b);
            }
            writeAudit(actor, "BACKUP_COMPENSATION", "BACKUP",
                    backupId.toString(), correlationId, null,
                    Map.of("failure_reason", reason == null ? "" : reason));
        });

        eventStreamService.publishEvent("BACKUP_COMPENSATION", Map.of(
                "backupId", backupId,
                "state", BackupState.COMPENSATION_REQUIRED.name(),
                "reason", reason == null ? "" : reason
        ));
    }

    @Transactional(readOnly = true)
    public BackupResponse getBackup(UUID backupId) {
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new ResourceNotFoundException("Backup not found: " + backupId));
        return BackupResponse.from(backup);
    }

    @Transactional(readOnly = true)
    public List<BackupResponse> listBackups(UUID userId, UUID fileId) {
        List<Backup> backups;
        if (userId != null && fileId != null) {
            backups = backupRepository.findByUserIdAndFileId(userId, fileId);
        } else if (userId != null) {
            backups = backupRepository.findByUserId(userId);
        } else if (fileId != null) {
            backups = backupRepository.findByFileId(fileId);
        } else {
            backups = backupRepository.findAll();
        }
        return backups.stream().map(BackupResponse::from).toList();
    }

    @Transactional
    public BackupResponse cancelBackup(UUID backupId, String correlationId) {
        String corr = safe(correlationId);
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new ResourceNotFoundException("Backup not found: " + backupId));
        if (backup.getState() == BackupState.COMPLETED || backup.getState() == BackupState.CANCELLED) {
            throw new ResourceConflictException(
                    "Backup " + backupId + " is in state " + backup.getState() + " and cannot be cancelled.");
        }
        Map<String, Object> before = snapshot(backup);
        if (backup.getLeaseId() != null) {
            try {
                teamAClient.releaseLease(backup.getLeaseId(), corr);
            } catch (RuntimeException e) {
                log.warn("Failed to release lease during cancellation: {}", e.getMessage());
            }
        }
        backup.setState(BackupState.CANCELLED);
        backup = backupRepository.save(backup);
        writeAudit(backup.getUserId().toString(), "BACKUP_CANCEL", "BACKUP",
                backupId.toString(), corr, before, snapshot(backup));
        eventStreamService.publishEvent("BACKUP_CANCELLED", Map.of("backupId", backupId));
        return BackupResponse.from(backup);
    }

    private void writeAudit(String actor, String action, String entityType, String entityId,
                             String correlationId, Map<String, Object> before, Map<String, Object> after) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setCorrelationId(safe(correlationId));
        entry.setSourceService("TEAM_C");
        entry.setBeforeValue(before);
        entry.setAfterValue(after);
        auditLogRepository.save(entry);
    }

    private Map<String, Object> snapshot(Backup b) {
        return Map.of(
                "state", b.getState() == null ? "" : b.getState().name(),
                "version_id", b.getVersionId() == null ? "" : b.getVersionId().toString(),
                "verification_status", b.getVerificationStatus() == null ? "" : b.getVerificationStatus().name(),
                "lease_id", b.getLeaseId() == null ? "" : b.getLeaseId(),
                "dedup_result_id", b.getDedupResultId() == null ? "" : b.getDedupResultId()
        );
    }

    private static String safe(String s) {
        if (s == null || s.isBlank()) return CorrelationContext.current();
        return s;
    }
}
