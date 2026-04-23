package com.fraudshield.common.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.UUID;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalystActionResponse(
    UUID id,
    UUID alertId,
    String analystId,
    String action,
    String notes,
    Instant createdAt
) {
}
