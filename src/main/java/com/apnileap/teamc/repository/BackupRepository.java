package com.apnileap.teamc.repository;

import com.apnileap.teamc.entity.Backup;
import com.apnileap.teamc.entity.BackupState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackupRepository extends JpaRepository<Backup, UUID> {
    Optional<Backup> findByIdempotencyKey(String idempotencyKey);
    List<Backup> findByUserId(UUID userId);
    List<Backup> findByFileId(UUID fileId);
    List<Backup> findByUserIdAndFileId(UUID userId, UUID fileId);
    List<Backup> findByState(BackupState state);
}
