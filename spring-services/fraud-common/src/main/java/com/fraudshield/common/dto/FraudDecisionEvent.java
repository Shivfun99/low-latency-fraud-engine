package com.fraudshield.common.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fraudshield.common.enums.DecisionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FraudDecisionEvent(
        UUID decisionId,
        UUID transactionId,
        String userId,
        BigDecimal amount,
        String location,
        double riskScore,
        DecisionType decision,
        double velocityScore,
        double geoAnomalyScore,
        double amountAnomalyScore,
        double mlScore,
        int processingTimeMs,
        List<String> triggeredRules,
        Instant timestamp
) {
}
