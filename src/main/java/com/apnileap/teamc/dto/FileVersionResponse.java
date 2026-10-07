package com.apnileap.teamc.dto;

import com.apnileap.teamc.entity.FileVersion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileVersionResponse(
        UUID versionId,
        UUID fileId,
        Integer versionNumber,
        String checksum,
        Long sizeBytes,
        String state,
        String merkleRoot,
        List<String> chunkRefs,
        String retentionPolicy,
        Instant createdAt
) {
    public static FileVersionResponse from(FileVersion v) {
        return new FileVersionResponse(
                v.getVersionId(), v.getFileId(), v.getVersionNumber(), v.getChecksum(),
                v.getSizeBytes(), v.getState(), v.getMerkleRoot(), v.getChunkRefs(),
                v.getRetentionPolicy(), v.getCreatedAt()
        );
    }
}
