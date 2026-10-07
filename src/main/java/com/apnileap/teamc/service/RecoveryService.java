package com.apnileap.teamc.service;

import com.apnileap.teamc.client.TeamBClient;
import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.RecoveryVerifyRequest;
import com.apnileap.teamc.dto.RecoveryVerifyResponse;
import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.FileVersion;
import com.apnileap.teamc.exception.ResourceNotFoundException;
import com.apnileap.teamc.repository.AuditLogRepository;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.FileVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryService {
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final AuditLogRepository auditLogRepository;
    private final TeamBClient teamBClient;

    @Transactional
    public RecoveryVerifyResponse verifyRecovery(RecoveryVerifyRequest request, String correlationId) {
        String corr = (correlationId == null || correlationId.isBlank()) ? CorrelationContext.current() : correlationId;
        FileEntity file = fileRepository.findById(request.fileId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + request.fileId()));

        List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(file.getFileId());
        if (versions.isEmpty()) {
            throw new ResourceNotFoundException("No versions exist to verify recovery for file: " + request.fileId());
        }

        FileVersion targetVersion;
        if (request.versionId() != null) {
            targetVersion = fileVersionRepository.findById(request.versionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target version not found: " + request.versionId()));
        } else {
            targetVersion = versions.get(0);
        }

        Instant now = Instant.now();
        Instant referencePoint = (request.targetPointInTime() != null) ? request.targetPointInTime() : now;

        long rpoSeconds = Math.abs(Duration.between(targetVersion.getCreatedAt(), referencePoint).toSeconds());
        boolean rpoCompliant = rpoSeconds <= 300; // Target RPO <= 5 minutes (300 seconds)

        long size = targetVersion.getSizeBytes() != null ? targetVersion.getSizeBytes() : 0L;
        long estimatedRtoSeconds = Math.max(10L, size / (50 * 1024 * 1024));
        boolean rtoCompliant = estimatedRtoSeconds <= 1800; // Target RTO <= 30 minutes (1800 seconds)

        boolean integrityOk = teamBClient.verifyIntegrity(file.getFileId(), targetVersion.getMerkleRoot(), corr);

        RecoveryVerifyResponse response = new RecoveryVerifyResponse(
                file.getFileId(),
                targetVersion.getVersionId(),
                integrityOk,
                rpoSeconds,
                rpoCompliant,
                estimatedRtoSeconds,
                rtoCompliant,
                integrityOk ? "VERIFIED" : "DEGRADED",
                now
        );

        AuditLog audit = new AuditLog();
        audit.setActor("RECOVERY_ENGINE");
        audit.setAction("RECOVERY_VERIFY");
        audit.setEntityType("RECOVERY");
        audit.setEntityId(targetVersion.getVersionId().toString());
        audit.setCorrelationId(corr);
        audit.setSourceService("TEAM_C");
        audit.setAfterValue(Map.of(
                "rpo_seconds", rpoSeconds,
                "rpo_compliant", rpoCompliant,
                "rto_seconds", estimatedRtoSeconds,
                "rto_compliant", rtoCompliant,
                "integrity_status", response.integrityCheckStatus()
        ));
        auditLogRepository.save(audit);

        return response;
    }
}
