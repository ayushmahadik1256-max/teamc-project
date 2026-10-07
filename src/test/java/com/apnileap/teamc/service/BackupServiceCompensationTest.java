package com.apnileap.teamc.service;

import com.apnileap.teamc.client.TeamAClient;
import com.apnileap.teamc.client.TeamBClient;
import com.apnileap.teamc.dto.AllocationDecision;
import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.entity.Backup;
import com.apnileap.teamc.entity.BackupState;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.exception.DownstreamServiceException;
import com.apnileap.teamc.repository.BackupRepository;
import com.apnileap.teamc.support.AbstractIntegrationTest;
import com.apnileap.teamc.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BackupServiceCompensationTest extends AbstractIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupRepository backupRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @MockBean
    private TeamAClient teamAClient;

    @MockBean
    private TeamBClient teamBClient;

    @Test
    void downstreamFailureAfterLeaseGrantTriggersCompensation() {
        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());
        String leaseId = "lease-" + UUID.randomUUID();

        when(teamAClient.requestScheduling(any(), any(), anyString(), anyString()))
                .thenReturn(new AllocationDecision("decision-1", leaseId, "GRANTED"));
        when(teamBClient.computeDedup(any(), anyString()))
                .thenThrow(new DownstreamServiceException("Team B unreachable"));

        CreateBackupRequest request = new CreateBackupRequest(
                user.getUserId(), file.getFileId(), "FULL", "NORMAL", "idem-" + UUID.randomUUID());

        assertThatThrownBy(() -> backupService.createBackup(request, "corr-1"))
                .isInstanceOf(DownstreamServiceException.class);

        verify(teamAClient, times(1)).releaseLease(eq(leaseId), anyString());

        Optional<Backup> saved = backupRepository.findByIdempotencyKey(request.idempotencyKey());
        assertThat(saved).isPresent();
        assertThat(saved.get().getState()).isEqualTo(BackupState.COMPENSATION_REQUIRED);
        assertThat(saved.get().getFailureReason()).contains("Team B unreachable");
    }
}
