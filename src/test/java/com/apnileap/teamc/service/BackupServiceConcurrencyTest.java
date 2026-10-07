package com.apnileap.teamc.service;

import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.repository.BackupRepository;
import com.apnileap.teamc.support.AbstractIntegrationTest;
import com.apnileap.teamc.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BackupServiceConcurrencyTest extends AbstractIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupRepository backupRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void concurrentRequestsWithSameIdempotencyKeyProduceExactlyOneBackup() throws InterruptedException {
        User user = testDataFactory.createUser();
        FileEntity file = testDataFactory.createFile(user.getUserId());
        String idempotencyKey = "idem-concurrent-" + UUID.randomUUID();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<Boolean>> results = IntStream.range(0, threadCount)
                .mapToObj(i -> executor.submit(() -> {
                    try {
                        startLatch.await();
                        CreateBackupRequest request = new CreateBackupRequest(
                                user.getUserId(), file.getFileId(), "FULL", "NORMAL", idempotencyKey);
                        backupService.createBackup(request, "corr-" + i);
                        return true;
                    } catch (Exception e) {
                        return false;
                    } finally {
                        doneLatch.countDown();
                    }
                }))
                .toList();

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = results.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();

        assertThat(successCount).isGreaterThanOrEqualTo(1);
        assertThat(backupRepository.count()).isEqualTo(1);
        assertThat(backupRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
    }
}
