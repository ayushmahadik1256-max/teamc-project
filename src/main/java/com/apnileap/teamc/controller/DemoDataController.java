package com.apnileap.teamc.controller;

import com.apnileap.teamc.config.CorrelationContext;
import com.apnileap.teamc.dto.ApiResponse;
import com.apnileap.teamc.dto.CreateBackupRequest;
import com.apnileap.teamc.dto.CreateFileRequest;
import com.apnileap.teamc.dto.CreateUserRequest;
import com.apnileap.teamc.dto.FileResponse;
import com.apnileap.teamc.dto.UserResponse;
import com.apnileap.teamc.entity.UserRole;
import com.apnileap.teamc.repository.UserRepository;
import com.apnileap.teamc.service.BackupService;
import com.apnileap.teamc.service.FileService;
import com.apnileap.teamc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoDataController {
    private final UserService userService;
    private final FileService fileService;
    private final BackupService backupService;
    private final UserRepository userRepository;

    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seedDemoData(HttpServletRequest http) {
        String corr = CorrelationContext.current();
        Map<String, Object> summary = new LinkedHashMap<>();

        // 1. Create or fetch demo users
        UserResponse employee;
        var empOpt = userRepository.findByEmail("ayush.mahadik@apnileap.edu");
        if (empOpt.isEmpty()) {
            employee = userService.createUser(new CreateUserRequest(
                    "ayush.mahadik@apnileap.edu", "Ayush Mahadik (Team C)", UserRole.EMPLOYEE), corr);
        } else {
            employee = UserResponse.from(empOpt.get());
        }

        UserResponse admin;
        var admOpt = userRepository.findByEmail("it.admin@apnileap.edu");
        if (admOpt.isEmpty()) {
            admin = userService.createUser(new CreateUserRequest(
                    "it.admin@apnileap.edu", "Campus IT Administrator", UserRole.IT_ADMIN), corr);
        } else {
            admin = UserResponse.from(admOpt.get());
        }

        UserResponse auditor;
        var audOpt = userRepository.findByEmail("compliance.auditor@apnileap.edu");
        if (audOpt.isEmpty()) {
            auditor = userService.createUser(new CreateUserRequest(
                    "compliance.auditor@apnileap.edu", "System Auditor", UserRole.AUDITOR), corr);
        } else {
            auditor = UserResponse.from(audOpt.get());
        }

        summary.put("users_created", 3);
        summary.put("demo_user_id", employee.userId());

        // 2. Register sample files
        FileResponse file1 = fileService.createFile(new CreateFileRequest(
                employee.userId(), "research_paper_smart_backup.pdf", "/campus/storage/docs/research_paper_smart_backup.pdf", "7_YEARS"
        ), corr);

        FileResponse file2 = fileService.createFile(new CreateFileRequest(
                employee.userId(), "student_records_sem4.sqlite", "/campus/db/student_records_sem4.sqlite", "PERMANENT"
        ), corr);

        summary.put("files_registered", 2);

        // 3. Run initial backups
        var backup1 = backupService.createBackup(new CreateBackupRequest(
                employee.userId(), file1.fileId(), "FULL", "HIGH", "demo-backup-init-1"
        ), corr);

        var backup2 = backupService.createBackup(new CreateBackupRequest(
                employee.userId(), file2.fileId(), "INCREMENTAL", "NORMAL", "demo-backup-init-2"
        ), corr);

        summary.put("backups_completed", 2);
        summary.put("message", "Demo environment initialized successfully with users, files, and verified backups.");

        return ResponseEntity.ok()
                .header(CorrelationContext.HEADER, corr)
                .body(ApiResponse.success(summary, corr));
    }
}
