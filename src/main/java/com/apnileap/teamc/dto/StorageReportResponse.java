package com.apnileap.teamc.dto;

import java.util.UUID;

public record StorageReportResponse(UUID fileId, String fileName, long totalBytes, long versionCount) {}
