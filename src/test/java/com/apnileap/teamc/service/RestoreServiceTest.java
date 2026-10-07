package com.apnileap.teamc.service;

import com.apnileap.teamc.client.TeamAClient;
import com.apnileap.teamc.dto.AllocationDecision;
import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.dto.CreateRestoreRequest;
import com.apnileap.teamc.dto.RestoreResponse;
import com.apnileap.teamc.entity.*;
import com.apnileap.teamc.exception.DownstreamServiceException;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.RestoreRepository;
import com.apnileap.teamc.support.AbstractIntegrationTest;
import com.apnileap.teamc.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RestoreServiceTest extends AbstractIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private RestoreService restoreService;

    @Autowired
    private RestoreRepository restoreRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @MockBean
    private TeamAClient teamAClient;

    @Test
    void successfulRestoreUpdatesFileCurrentVersionAndIsIdempotent() {
        when(teamAClient.requestScheduling(any(), any(), anyString(), anyString()))
                .thenReturn(new AllocationDecision("decision-1", "lease-1", "GRANTED"));

        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());

        CreateBackupRequest backupRequest = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", "idem-backup-" + UUID.randomUUID());
        var backup = backupService.createBackup(backupRequest, "corr-backup");

        CreateRestoreRequest restoreRequest = new CreateRestoreRequest(
                backup.backupId(), user.getUserId(), backup.versionId(), "idem-restore-" + UUID.randomUUID());

        RestoreResponse first = restoreService.commitRestore(restoreRequest, "corr-restore-1");
        RestoreResponse second = restoreService.commitRestore(restoreRequest, "corr-restore-2");

        assertThat(second.restoreId()).isEqualTo(first.restoreId());
        assertThat(first.state()).isEqualTo("RESTORED");

        FileEntity updatedFile = fileRepository.findById(file.getFileId()).orElseThrow();
        assertThat(updatedFile.getCurrentVersionId()).isEqualTo(backup.versionId());
    }

    @Test
    void restoreCompensatesWhenTeamAFailsToGrantLease() {
        when(teamAClient.requestScheduling(any(), any(), anyString(), anyString()))
                .thenReturn(new AllocationDecision("decision-ok", "lease-ok", "GRANTED"))
                .thenThrow(new DownstreamServiceException("Team A unavailable"));

        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());

        CreateBackupRequest backupRequest = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", "idem-backup-" + UUID.randomUUID());
        var backup = backupService.createBackup(backupRequest, "corr-backup");

        CreateRestoreRequest restoreRequest = new CreateRestoreRequest(
                backup.backupId(), user.getUserId(), backup.versionId(), "idem-restore-" + UUID.randomUUID());

        assertThatThrownBy(() -> restoreService.commitRestore(restoreRequest, "corr-restore"))
                .isInstanceOf(DownstreamServiceException.class);

        Optional<Restore> saved = restoreRepository.findByIdempotencyKey(restoreRequest.idempotencyKey());
        assertThat(saved).isPresent();
        assertThat(saved.get().getState()).isEqualTo(RestoreState.COMPENSATION_REQUIRED);
    }
}
