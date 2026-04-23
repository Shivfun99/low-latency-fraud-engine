package com.fraudshield.common.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalystActionRequest(
        String action,
        String analystId,
        String notes
) {
}
