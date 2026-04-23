package com.fraudshield.orchestrator.service;
import com.fraudshield.common.enums.DecisionType;
import java.util.List;
public record ScoreResult(
        double riskScore,
        DecisionType decision,
        double velocityScore,
        double geoAnomalyScore,
        double amountAnomalyScore,
        double mlScore,
        int processingTimeMs,
        List<String> triggeredRules
) {
}
