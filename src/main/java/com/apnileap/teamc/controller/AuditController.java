package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.ApiResponse;
import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLog>>> searchAudit(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String correlationId,
            HttpServletRequest http) {
        String requestCorrelationId = correlationId(http);
        List<AuditLog> results = auditService.search(actor, action, correlationId);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, requestCorrelationId)
                .body(ApiResponse.success(results, requestCorrelationId));
    }

    private static String correlationId(HttpServletRequest http) {
        Object attr = http.getAttribute(CorrelationContext.REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String header = http.getHeader(CorrelationContext.HEADER);
        return header == null ? "" : header;
    }
}
