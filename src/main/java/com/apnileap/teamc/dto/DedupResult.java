package com.apnileap.teamc.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record DedupResult(
        @JsonAlias("dedup_result_id") String dedupResultId,
        @JsonAlias("unique_chunks") int uniqueChunks,
        @JsonAlias("duplicate_chunks") int duplicateChunks,
        @JsonAlias("savings_ratio") double savingsRatio,
        @JsonAlias("merkle_root") String merkleRoot,
        @JsonAlias("chunk_refs") List<String> chunkRefs
) {
    public DedupResult(String dedupResultId, int uniqueChunks, int duplicateChunks, double savingsRatio, String merkleRoot) {
        this(dedupResultId, uniqueChunks, duplicateChunks, savingsRatio, merkleRoot, List.of());
    }
}
