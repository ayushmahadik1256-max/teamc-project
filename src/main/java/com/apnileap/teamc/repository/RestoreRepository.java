package com.apnileap.teamc.repository;

import com.apnileap.teamc.entity.Restore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestoreRepository extends JpaRepository<Restore, UUID> {
    Optional<Restore> findByIdempotencyKey(String idempotencyKey);
    List<Restore> findByBackupId(UUID backupId);
}
