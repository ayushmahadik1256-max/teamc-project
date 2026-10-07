package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.ApiResponse;
import com.apnileap.teamc.dto.RecoveryVerifyRequest;
import com.apnileap.teamc.dto.RecoveryVerifyResponse;
import com.apnileap.teamc.service.RecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecoveryController {
    private final RecoveryService recoveryService;

    @PostMapping({"/internal/v1/recovery/verify", "/api/v1/recovery/verify"})
    public ResponseEntity<ApiResponse<RecoveryVerifyResponse>> verifyRecovery(
            @RequestBody @Valid RecoveryVerifyRequest request,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        RecoveryVerifyResponse response = recoveryService.verifyRecovery(request, correlationId);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(response, correlationId));
    }

    private static String correlationId(HttpServletRequest http) {
        Object attr = http.getAttribute(CorrelationContext.REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String header = http.getHeader(CorrelationContext.HEADER);
        return header == null ? "" : header;
    }
}
