package com.apnileap.teamc.service;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.CreateUserRequest;
import com.apnileap.teamc.dto.UserResponse;
import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.exception.ResourceConflictException;
import com.apnileap.teamc.exception.ResourceNotFoundException;
import com.apnileap.teamc.repository.AuditLogRepository;
import com.apnileap.teamc.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public UserService(UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, String correlationId) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException("A user with email " + request.email() + " already exists.");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        User saved = userRepository.saveAndFlush(user);

        AuditLog log = new AuditLog();
        log.setActor("SELF_SIGNUP");
        log.setAction("USER_CREATE");
        log.setEntityType("USER");
        log.setEntityId(saved.getUserId().toString());
        log.setCorrelationId(safe(correlationId));
        log.setSourceService("TEAM_C");
        log.setAfterValue(Map.of("email", saved.getEmail(), "role", saved.getRole().name()));
        auditLogRepository.save(log);

        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    private static String safe(String s) {
        if (s == null || s.isBlank()) return CorrelationContext.current();
        return s;
    }
}
