package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.ApiResponse;
import com.apnileap.teamc.dto.BackupResponse;
import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.service.BackupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backups")
@RequiredArgsConstructor
public class BackupController {
    private final BackupService backupService;

    @PostMapping
    public ResponseEntity<ApiResponse<BackupResponse>> createBackup(
            @RequestBody @Valid CreateBackupRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        String finalIdempotencyKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : request.idempotencyKey();

        if (finalIdempotencyKey == null || finalIdempotencyKey.isBlank()) {
            finalIdempotencyKey = UUID.randomUUID().toString();
        }

        CreateBackupRequest effectiveRequest = new CreateBackupRequest(
                request.userId(), request.fileId(), request.backupType(),
                request.priority(), finalIdempotencyKey);

        BackupResponse response = backupService.createBackup(effectiveRequest, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(response, correlationId));
    }

    @GetMapping("/{backupId}")
    public ResponseEntity<ApiResponse<BackupResponse>> getBackup(
            @PathVariable UUID backupId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(backupService.getBackup(backupId), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BackupResponse>>> listBackups(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID fileId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(backupService.listBackups(userId, fileId), correlationId));
    }

    @PostMapping("/{backupId}/cancel")
    public ResponseEntity<ApiResponse<BackupResponse>> cancelBackup(
            @PathVariable UUID backupId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(backupService.cancelBackup(backupId, correlationId), correlationId));
    }

    private static String correlationId(HttpServletRequest http) {
        Object attr = http.getAttribute(CorrelationContext.REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String header = http.getHeader(CorrelationContext.HEADER);
        return header == null ? "" : header;
    }
}
