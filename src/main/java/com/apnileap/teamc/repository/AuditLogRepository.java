package com.apnileap.teamc.repository;

import com.apnileap.teamc.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:actor IS NULL OR a.actor = :actor) AND " +
           "(:action IS NULL OR a.action = :action) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> search(@Param("actor") String actor, @Param("action") String action);
}
