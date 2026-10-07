package com.apnileap.teamc.service;

import com.apnileap.teamc.dto.BackupResponse;
import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.repository.BackupRepository;
import com.apnileap.teamc.support.AbstractIntegrationTest;
import com.apnileap.teamc.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackupServiceIdempotencyTest extends AbstractIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupRepository backupRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void duplicateIdempotencyKeyReturnsOriginalBackupAndCreatesNoNewRecord() {
        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());
        String idempotencyKey = "idem-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        CreateBackupRequest request = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", idempotencyKey);

        BackupResponse first = backupService.createBackup(request, correlationId);
        BackupResponse second = backupService.createBackup(request, correlationId);

        assertThat(second.backupId()).isEqualTo(first.backupId());
        assertThat(backupRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
        assertThat(backupRepository.count()).isEqualTo(1);
    }

    @Test
    void successfulBackupReachesCompletedStateWithMockedDownstreamServices() {
        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());

        CreateBackupRequest request = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", "idem-" + UUID.randomUUID());

        BackupResponse response = backupService.createBackup(request, "corr-" + UUID.randomUUID());
        assertThat(response.state()).isEqualTo("COMPLETED");
        assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(response.versionId()).isNotNull();
    }
}
