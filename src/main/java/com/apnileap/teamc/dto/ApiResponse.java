package com.apnileap.teamc.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApiResponse<T>(T data, Map<String, String> meta) {
    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(data, buildMeta(correlationId));
    }

    public static Map<String, String> buildMeta(String correlationId) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("correlation_id", correlationId == null ? "" : correlationId);
        meta.put("api_version", "v1");
        return meta;
    }
}
