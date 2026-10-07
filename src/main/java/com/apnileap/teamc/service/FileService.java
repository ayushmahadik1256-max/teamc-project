package com.apnileap.teamc.service;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.CreateFileRequest;
import com.apnileap.teamc.dto.FileResponse;
import com.apnileap.teamc.dto.FileVersionResponse;
import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.exception.ResourceNotFoundException;
import com.apnileap.teamc.repository.AuditLogRepository;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.FileVersionRepository;
import com.apnileap.teamc.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileService {
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public FileService(FileRepository fileRepository,
                        FileVersionRepository fileVersionRepository,
                        UserRepository userRepository,
                        AuditLogRepository auditLogRepository,
                        ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FileResponse createFile(CreateFileRequest request, String correlationId) {
        userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        FileEntity file = new FileEntity();
        file.setUserId(request.userId());
        file.setFileName(request.fileName());
        file.setSourcePath(request.sourcePath());
        file.setRetentionPolicy(request.retentionPolicy() == null ? "DEFAULT" : request.retentionPolicy());

        FileEntity saved = fileRepository.save(file);

        writeAudit(request.userId().toString(), "FILE_CREATE", "FILE",
                saved.getFileId().toString(), correlationId, null,
                Map.of("file_name", saved.getFileName(),
                        "retention_policy", saved.getRetentionPolicy()));

        return FileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public FileResponse getFile(UUID fileId) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        return FileResponse.from(file);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> listFiles(UUID userId) {
        List<FileEntity> files = (userId != null)
                ? fileRepository.findByUserId(userId)
                : fileRepository.findAll();
        return files.stream().map(FileResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FileVersionResponse> getVersions(UUID fileId) {
        fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        return fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId)
                .stream().map(FileVersionResponse::from).toList();
    }

    @Transactional
    public FileResponse updateFile(UUID fileId, Map<String, String> updates, String actor, String correlationId) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("retention_policy", file.getRetentionPolicy());
        before.put("storage_location", file.getStorageLocation());

        if (updates.containsKey("retentionPolicy")) {
            file.setRetentionPolicy(updates.get("retentionPolicy"));
        } else if (updates.containsKey("retention_policy")) {
            file.setRetentionPolicy(updates.get("retention_policy"));
        }

        if (updates.containsKey("storageLocation")) {
            file.setStorageLocation(updates.get("storageLocation"));
        } else if (updates.containsKey("storage_location")) {
            file.setStorageLocation(updates.get("storage_location"));
        }

        FileEntity saved = fileRepository.save(file);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("retention_policy", saved.getRetentionPolicy());
        after.put("storage_location", saved.getStorageLocation());

        writeAudit(actor == null ? "ADMIN" : actor, "FILE_METADATA_UPDATE", "FILE",
                fileId.toString(), correlationId, before, after);

        return FileResponse.from(saved);
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
        if (after != null) {
            try {
                objectMapper.writeValueAsString(after);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialise audit after-value to JSON: {}", e.getMessage());
            }
        }
        auditLogRepository.save(entry);
    }

    private static String safe(String s) {
        if (s == null || s.isBlank()) return CorrelationContext.current();
        return s;
    }
}
