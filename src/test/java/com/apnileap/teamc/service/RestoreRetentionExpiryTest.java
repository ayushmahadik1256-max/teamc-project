package com.apnileap.teamc.service;

import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.dto.CreateRestoreRequest;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.FileVersion;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.exception.ResourceConflictException;
import com.apnileap.teamc.repository.FileVersionRepository;
import com.apnileap.teamc.support.AbstractIntegrationTest;
import com.apnileap.teamc.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestoreRetentionExpiryTest extends AbstractIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private RestoreService restoreService;

    @Autowired
    private FileVersionRepository fileVersionRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void restoreFailsWhenRetentionPolicyExpiresTargetVersion() {
        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());

        CreateBackupRequest backupRequest = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", "idem-backup-" + UUID.randomUUID());
        var backup = backupService.createBackup(backupRequest, "corr-backup");

        FileVersion version = fileVersionRepository.findById(backup.versionId()).orElseThrow();
        version.setState("EXPIRED");
        fileVersionRepository.save(version);

        CreateRestoreRequest restoreRequest = new CreateRestoreRequest(
                backup.backupId(), user.getUserId(), backup.versionId(), "idem-restore-" + UUID.randomUUID());

        assertThatThrownBy(() -> restoreService.commitRestore(restoreRequest, "corr-expire-test"))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("EXPIRED");
    }
}
