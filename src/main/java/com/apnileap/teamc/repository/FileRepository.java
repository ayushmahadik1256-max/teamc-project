package com.apnileap.teamc.repository;

import com.apnileap.teamc.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileEntity, UUID> {
    List<FileEntity> findByUserId(UUID userId);
}
