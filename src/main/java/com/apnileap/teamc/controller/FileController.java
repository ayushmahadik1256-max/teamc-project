package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.*;
import com.apnileap.teamc.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    @PostMapping("/api/v1/files")
    public ResponseEntity<ApiResponse<FileResponse>> createFile(
            @RequestBody @Valid CreateFileRequest request,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        FileResponse response = fileService.createFile(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(response, correlationId));
    }

    @GetMapping("/api/v1/files/{fileId}")
    public ResponseEntity<ApiResponse<FileResponse>> getFile(
            @PathVariable UUID fileId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(fileService.getFile(fileId), correlationId));
    }

    @GetMapping("/api/v1/files")
    public ResponseEntity<ApiResponse<List<FileResponse>>> listFiles(
            @RequestParam(required = false) UUID userId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(fileService.listFiles(userId), correlationId));
    }

    @GetMapping("/api/v1/files/{fileId}/versions")
    public ResponseEntity<ApiResponse<List<FileVersionResponse>>> getVersions(
            @PathVariable UUID fileId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(fileService.getVersions(fileId), correlationId));
    }

    @PatchMapping({"/internal/v1/files/{fileId}", "/api/v1/files/{fileId}"})
    public ResponseEntity<ApiResponse<FileResponse>> updateFile(
            @PathVariable UUID fileId,
            @RequestBody Map<String, String> updates,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(
                        fileService.updateFile(fileId, updates, actor, correlationId), correlationId));
    }

    private static String correlationId(HttpServletRequest http) {
        Object attr = http.getAttribute(CorrelationContext.REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String header = http.getHeader(CorrelationContext.HEADER);
        return header == null ? "" : header;
    }
}
