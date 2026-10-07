package com.apnileap.teamc.service;

import com.apnileap.teamc.dto.StorageReportResponse;
import com.apnileap.teamc.entity.FileEntity;
import com.apnileap.teamc.entity.FileVersion;
import com.apnileap.teamc.repository.FileRepository;
import com.apnileap.teamc.repository.FileVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;

    @Transactional(readOnly = true)
    public List<StorageReportResponse> getStorageReport() {
        List<FileEntity> files = fileRepository.findAll();
        return files.stream().map(file -> {
            List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(file.getFileId());
            long totalBytes = versions.stream()
                    .mapToLong(v -> v.getSizeBytes() == null ? 0L : v.getSizeBytes())
                    .sum();
            return new StorageReportResponse(file.getFileId(), file.getFileName(), totalBytes, versions.size());
        }).toList();
    }
}
