package com.apnileap.teamc.service;

import com.apnileap.teamc.entity.AuditLog;
import com.apnileap.teamc.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AuditLog> search(String actor, String action, String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return auditLogRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId);
        }
        String cleanActor = (actor == null || actor.isBlank()) ? null : actor;
        String cleanAction = (action == null || action.isBlank()) ? null : action;
        return auditLogRepository.search(cleanActor, cleanAction);
    }
}
