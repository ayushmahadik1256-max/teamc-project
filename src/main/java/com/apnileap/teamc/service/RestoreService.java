package com.apnileap.teamc.service;

import com.apnileap.teamc.client.TeamAClient;
import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.AllocationDecision;
import com.apnileap.teamc.dto.CreateRestoreRequest;
import com.apnileap.teamc.dto.RestoreResponse;
import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.entity.Backup;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.FileVersion;
import com.apnileap.teamc.entity.Restore;
import com.apnileap.teamc.entity.RestoreState;
import com.apnileap.teamc.exception.DownstreamServiceException;
import com.apnileap.teamc.exception.IdempotencyConflictException;
import com.apnileap.teamc.exception.ResourceConflictException;
import com.apnileap.teamc.exception.ResourceNotFoundException;
import com.apnileap.teamc.repository.AuditLogRepository;
import com.apnileap.teamc.repository.BackupRepository;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.FileVersionRepository;
import com.apnileap.teamc.repository.RestoreRepository;
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
public class RestoreService {
    private final RestoreRepository restoreRepository;
    private final BackupRepository backupRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileRepository fileRepository;
    private final AuditLogRepository auditLogRepository;
    private final TeamAClient teamAClient;
    private final EventStreamService eventStreamService;
    private final TransactionTemplate requiresNewTx;
    private final TransactionTemplate standardTx;

    public RestoreService(RestoreRepository restoreRepository,
                           BackupRepository backupRepository,
                           FileVersionRepository fileVersionRepository,
                           FileRepository fileRepository,
                           AuditLogRepository auditLogRepository,
                           TeamAClient teamAClient,
                           EventStreamService eventStreamService,
                           PlatformTransactionManager txManager) {
        this.restoreRepository = restoreRepository;
        this.backupRepository = backupRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.fileRepository = fileRepository;
        this.auditLogRepository = auditLogRepository;
        this.teamAClient = teamAClient;
        this.eventStreamService = eventStreamService;

        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.standardTx = new TransactionTemplate(txManager);
    }

    public RestoreResponse commitRestore(CreateRestoreRequest request, String correlationId) {
        String corr = safe(correlationId);

        // 1. Idempotency replay check
        Optional<Restore> existing = restoreRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay for restore key={} -> returning existing restoreId={}",
                    request.idempotencyKey(), existing.get().getRestoreId());
            return RestoreResponse.from(existing.get());
        }

        Backup backup = backupRepository.findById(request.backupId())
                .orElseThrow(() -> new ResourceNotFoundException("Backup not found: " + request.backupId()));

        UUID versionId = (request.versionId() != null) ? request.versionId() : backup.getVersionId();
        if (versionId == null) {
            throw new ResourceNotFoundException(
                    "Backup " + backup.getBackupId() + " has no committed version to restore.");
        }

        FileVersion targetVersion = fileVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("File version not found: " + versionId));

        // Retention-policy validation check (reject restore if version expired)
        if ("EXPIRED".equalsIgnoreCase(targetVersion.getState())) {
            throw new ResourceConflictException("Cannot restore version " + versionId + " because its retention policy has EXPIRED.");
        }

        // 2. Insert initial REQUESTED row in separate transaction so compensation can update it
        Restore restore;
        try {
            restore = requiresNewTx.execute(status -> {
                Optional<Restore> already = restoreRepository.findByIdempotencyKey(request.idempotencyKey());
                if (already.isPresent()) {
                    return already.get();
                }
                Restore r = new Restore();
                r.setBackupId(backup.getBackupId());
                r.setVersionId(versionId);
                r.setUserId(request.userId());
                r.setState(RestoreState.REQUESTED);
                r.setIdempotencyKey(request.idempotencyKey());
                r.setCorrelationId(corr);
                return restoreRepository.saveAndFlush(r);
            });
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotency race on restore key={} - reading winner", request.idempotencyKey());
            Restore winner = restoreRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "Concurrent insert for restore idempotency_key=" + request.idempotencyKey()
                                    + " could not be resolved."));
            return RestoreResponse.from(winner);
        }

        if (restore == null) {
            throw new IdempotencyConflictException("Failed to initialize restore record for key=" + request.idempotencyKey());
        }

        final UUID restoreId = restore.getRestoreId();
        eventStreamService.publishEvent("RESTORE_REQUESTED", Map.of("restoreId", restoreId, "backupId", backup.getBackupId()));

        try {
            // 3. Request worker lease from Team A
            AllocationDecision decision = teamAClient.requestScheduling(
                    backup.getBackupId(), backup.getFileId(), "HIGH", corr);

            requiresNewTx.executeWithoutResult(status -> {
                Restore r = restoreRepository.findById(restoreId).orElseThrow();
                r.setLeaseId(decision.leaseId());
                r.setState(RestoreState.LEASED);
                restoreRepository.saveAndFlush(r);
            });
            restore.setLeaseId(decision.leaseId());
            restore.setState(RestoreState.LEASED);

            // 4. Atomically commit restore + update file pointer + write audit
            standardTx.executeWithoutResult(status -> {
                FileEntity file = fileRepository.findById(backup.getFileId())
                        .orElseThrow(() -> new ResourceNotFoundException("File not found: " + backup.getFileId()));
                file.setCurrentVersionId(versionId);
                fileRepository.save(file);

                Restore r = restoreRepository.findById(restoreId).orElseThrow();
                r.setState(RestoreState.RESTORED);
                restoreRepository.save(r);

                restore.setState(RestoreState.RESTORED);

                writeAudit(request.userId().toString(), "RESTORE_COMMIT", "RESTORE",
                        restoreId.toString(), corr, null, snapshot(r));
            });

            eventStreamService.publishEvent("RESTORE_COMPLETED", RestoreResponse.from(restore));
            return RestoreResponse.from(restore);

        } catch (DownstreamServiceException e) {
            log.error("Restore commit failed for restoreId={}, compensating.", restoreId, e);
            recordCompensation(restoreId, restore.getLeaseId(), e.getMessage(), request.userId().toString(), corr);
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected failure for restoreId={}, compensating.", restoreId, e);
            recordCompensation(restoreId, restore.getLeaseId(), e.getMessage(), request.userId().toString(), corr);
            throw e;
        }
    }

    @Transactional
    public RestoreResponse compensateRestore(UUID restoreId, String correlationId) {
        String corr = safe(correlationId);
        Restore restore = restoreRepository.findById(restoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Restore not found: " + restoreId));

        Map<String, Object> before = snapshot(restore);
        if (restore.getLeaseId() != null) {
            try {
                teamAClient.releaseLease(restore.getLeaseId(), corr);
            } catch (RuntimeException e) {
                log.warn("Failed to release lease during compensation: {}", e.getMessage());
            }
        }
        restore.setState(RestoreState.COMPENSATION_REQUIRED);
        restore = restoreRepository.save(restore);
        writeAudit(restore.getUserId().toString(), "RESTORE_MANUAL_COMPENSATION", "RESTORE",
                restoreId.toString(), corr, before, snapshot(restore));
        return RestoreResponse.from(restore);
    }

    @Transactional(readOnly = true)
    public RestoreResponse getRestore(UUID restoreId) {
        Restore restore = restoreRepository.findById(restoreId)
                .orElseThrow(() -> new ResourceNotFoundException("Restore not found: " + restoreId));
        return RestoreResponse.from(restore);
    }

    @Transactional(readOnly = true)
    public List<RestoreResponse> listRestores(UUID backupId) {
        List<Restore> list = (backupId != null)
                ? restoreRepository.findByBackupId(backupId)
                : restoreRepository.findAll();
        return list.stream().map(RestoreResponse::from).toList();
    }

    private void recordCompensation(UUID restoreId, String leaseId, String reason, String actor, String correlationId) {
        if (leaseId != null) {
            try {
                teamAClient.releaseLease(leaseId, correlationId);
            } catch (RuntimeException ex) {
                log.warn("Lease release threw during restore compensation: {}", ex.getMessage());
            }
        }

        requiresNewTx.executeWithoutResult(status -> {
            Restore r = restoreRepository.findById(restoreId).orElse(null);
            if (r != null) {
                r.setState(RestoreState.COMPENSATION_REQUIRED);
                r.setFailureReason(reason);
                restoreRepository.saveAndFlush(r);
            }
            writeAudit(actor, "RESTORE_COMPENSATION", "RESTORE",
                    restoreId.toString(), correlationId, null,
                    Map.of("failure_reason", reason == null ? "" : reason));
        });

        eventStreamService.publishEvent("RESTORE_COMPENSATION", Map.of("restoreId", restoreId, "reason", reason == null ? "" : reason));
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

    private Map<String, Object> snapshot(Restore r) {
        return Map.of(
                "state", r.getState() == null ? "" : r.getState().name(),
                "lease_id", r.getLeaseId() == null ? "" : r.getLeaseId(),
                "version_id", r.getVersionId() == null ? "" : r.getVersionId().toString()
        );
    }

    private static String safe(String s) {
        if (s == null || s.isBlank()) return CorrelationContext.current();
        return s;
    }
}
