package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.ApiResponse;
import com.apnileap.teamc.dto.CreateRestoreRequest;
import com.apnileap.teamc.dto.RestoreResponse;
import com.apnileap.teamc.service.RestoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RestoreController {
    private final RestoreService restoreService;

    @PostMapping({"/internal/v1/restores/commit", "/api/v1/restores"})
    public ResponseEntity<ApiResponse<RestoreResponse>> commitRestore(
            @RequestBody @Valid CreateRestoreRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        String finalIdempotencyKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : request.idempotencyKey();

        if (finalIdempotencyKey == null || finalIdempotencyKey.isBlank()) {
            finalIdempotencyKey = UUID.randomUUID().toString();
        }

        CreateRestoreRequest effectiveRequest = new CreateRestoreRequest(
                request.backupId(), request.userId(), request.versionId(), finalIdempotencyKey);

        RestoreResponse response = restoreService.commitRestore(effectiveRequest, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(response, correlationId));
    }

    @PostMapping({"/internal/v1/restores/{restoreId}/compensate", "/internal/v1/restores/compensate", "/api/v1/restores/{restoreId}/compensate"})
    public ResponseEntity<ApiResponse<RestoreResponse>> compensateRestore(
            @PathVariable(required = false) UUID restoreId,
            @RequestParam(required = false) UUID id,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        UUID targetId = restoreId != null ? restoreId : id;
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(
                        restoreService.compensateRestore(targetId, correlationId), correlationId));
    }

    @GetMapping({"/internal/v1/restores/{restoreId}", "/api/v1/restores/{restoreId}"})
    public ResponseEntity<ApiResponse<RestoreResponse>> getRestore(
            @PathVariable UUID restoreId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(restoreService.getRestore(restoreId), correlationId));
    }

    @GetMapping({"/internal/v1/restores", "/api/v1/restores"})
    public ResponseEntity<ApiResponse<List<RestoreResponse>>> listRestores(
            @RequestParam(required = false) UUID backupId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(restoreService.listRestores(backupId), correlationId));
    }

    private static String correlationId(HttpServletRequest http) {
        Object attr = http.getAttribute(CorrelationContext.REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String header = http.getHeader(CorrelationContext.HEADER);
        return header == null ? "" : header;
    }
}
