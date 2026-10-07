package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.ApiResponse;
import com.apnileap.teamc.dto.CreateUserRequest;
import com.apnileap.teamc.dto.UserResponse;
import com.apnileap.teamc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @RequestBody @Valid CreateUserRequest request,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        UserResponse response = userService.createUser(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(response, correlationId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable UUID userId,
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(userService.getUser(userId), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers(
            HttpServletRequest http) {
        String correlationId = correlationId(http);
        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, correlationId)
                .body(ApiResponse.success(userService.listUsers(), correlationId));
    }

    private static String correlationId(HttpServletRequest http) {
        Object attr = http.getAttribute(CorrelationContext.REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String header = http.getHeader(CorrelationContext.HEADER);
        return header == null ? "" : header;
    }
}
