package com.apnileap.teamc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "file_versions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"file_id", "version_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class FileVersion {
    @Id
    @GeneratedValue
    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(nullable = false)
    private String state = "COMMITTED";

    @Column(name = "merkle_root")
    private String merkleRoot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chunk_refs", columnDefinition = "jsonb")
    private List<String> chunkRefs;

    @Column(name = "dedup_result_id")
    private String dedupResultId;

    @Column(name = "retention_policy", nullable = false)
    private String retentionPolicy = "DEFAULT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (state == null) {
            state = "COMMITTED";
        }
    }
}
