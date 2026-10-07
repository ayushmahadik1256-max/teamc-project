package com.apnileap.teamc.support;

import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.User;
import com.apnileap.teamc.entity.UserRole;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestDataFactory {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    public User createUser() {
        User user = new User();
        user.setEmail("test-" + UUID.randomUUID() + "@apnileap.test");
        user.setDisplayName("Test User");
        user.setRole(UserRole.EMPLOYEE);
        return userRepository.save(user);
    }

    public FileEntity createFile(UUID userId) {
        FileEntity file = new FileEntity();
        file.setUserId(userId);
        file.setFileName("report-" + UUID.randomUUID() + ".pdf");
        file.setSourcePath("/docs/report.pdf");
        file.setRetentionPolicy("DEFAULT");
        return fileRepository.save(file);
    }
}
