package com.apnileap.teamc.service;

import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.dto.RecoveryVerifyRequest;
import com.apnileap.teamc.dto.RecoveryVerifyResponse;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.support.AbstractIntegrationTest;
import com.apnileap.teamc.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryVerificationTest extends AbstractIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void verifyRecoveryDemonstratesRpoAndRtoCompliance() {
        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());

        CreateBackupRequest backupRequest = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", "idem-rec-" + UUID.randomUUID());
        var backup = backupService.createBackup(backupRequest, "corr-rec-1");

        RecoveryVerifyRequest request = new RecoveryVerifyRequest(
                file.getFileId(), backup.versionId(), Instant.now());

        RecoveryVerifyResponse response = recoveryService.verifyRecovery(request, "corr-rec-verify");

        assertThat(response.consistent()).isTrue();
        assertThat(response.rpoCompliant()).isTrue();
        assertThat(response.rtoCompliant()).isTrue();
        assertThat(response.integrityCheckStatus()).isEqualTo("VERIFIED");
    }
}
