package com.apnileap.teamc.dto;

import java.util.List;
import java.util.Map;

public record ErrorResponse(ErrorBody error, Map<String, String> meta) {
    public record ErrorBody(String code, String message, List<String> details) {}

    public static ErrorResponse of(String code, String message, List<String> details, String correlationId) {
        return new ErrorResponse(
                new ErrorBody(code, message, details),
                ApiResponse.buildMeta(correlationId)
        );
    }
}
