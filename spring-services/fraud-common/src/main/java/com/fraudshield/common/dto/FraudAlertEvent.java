package com.fraudshield.common.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fraudshield.common.enums.AlertSeverity;
import com.fraudshield.common.enums.AlertStatus;
import com.fraudshield.common.enums.DecisionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FraudAlertEvent(
        UUID alertId,
        UUID transactionId,
        UUID decisionId,
        String userId,
        BigDecimal amount,
        String location,
        double riskScore,
        DecisionType decision,
        double velocityScore,
        double geoAnomalyScore,
        double amountAnomalyScore,
        AlertSeverity severity,
        AlertStatus status,
        boolean actionable,
        String source,
        int processingTimeMs,
        String description,
        List<String> triggeredRules,
        Boolean isSandbox,
        Instant timestamp
) {
}
