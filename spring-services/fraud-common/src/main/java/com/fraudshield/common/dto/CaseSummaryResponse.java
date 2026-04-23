package com.fraudshield.common.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fraudshield.common.enums.AlertSeverity;
import com.fraudshield.common.enums.AlertStatus;
import com.fraudshield.common.enums.DecisionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CaseSummaryResponse(
        UUID alertId,
        UUID decisionId,
        UUID transactionId,
        String userId,
        BigDecimal amount,
        String location,
        double riskScore,
        DecisionType decision,
        AlertSeverity severity,
        AlertStatus status,
        String description,
        Instant createdAt,
        Instant resolvedAt
) {
}
