package com.fraudshield.common.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;
public record ApiError(
    String message,
    String path,
    int status,
    String traceId,
    Map<String, String> details,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp
) {
    public ApiError(String message, String path, int status, String traceId) {
        this(message, path, status, traceId, null, LocalDateTime.now());
    }
    public ApiError(String message, String path, int status, String traceId, Map<String, String> details) {
        this(message, path, status, traceId, details, LocalDateTime.now());
    }
}
