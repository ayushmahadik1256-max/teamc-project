package com.apnileap.teamc.repository;

import com.apnileap.teamc.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId);
    Optional<FileVersion> findTopByFileIdOrderByVersionNumberDesc(UUID fileId);
    Optional<FileVersion> findByFileIdAndVersionNumber(UUID fileId, Integer versionNumber);
}
