#include "rule_engine.h"
#include <iostream>
#include <cmath>
namespace fraud {
RuleEngine::RuleEngine() {
    std::cout << "[RuleEngine] Initialized with thresholds: "
              << "BLOCK>=" << BLOCK_THRESHOLD
              << " REVIEW>=" << REVIEW_THRESHOLD << std::endl;
}
ScoringDecision RuleEngine::applyRules(
    const TransactionContext& txn,
    const NormalizedFeatures& features,
    const ModelPrediction& prediction)
{
    ScoringDecision decision;
    auto rules = detectTriggeredRules(txn, features, prediction);
    decision.triggered_rules = rules;
    double adjusted_risk = applyRuleBoosts(
        prediction.fraud_probability, txn, features, rules
    );
    decision.final_risk_score = std::min(1.0, std::max(0.0, adjusted_risk));
    decision.decision = scoreToDecision(decision.final_risk_score);
    decision.severity = scoreToSeverity(decision.final_risk_score);
    return decision;
}
std::string RuleEngine::scoreToDecision(double risk_score) {
    if (risk_score >= BLOCK_THRESHOLD) return "BLOCK";
    if (risk_score >= REVIEW_THRESHOLD) return "REVIEW";
    return "ALLOW";
}
std::string RuleEngine::scoreToSeverity(double risk_score) {
    if (risk_score >= 0.90) return "CRITICAL";
    if (risk_score >= 0.70) return "HIGH";
    if (risk_score >= 0.50) return "MEDIUM";
    return "LOW";
}
std::vector<std::string> RuleEngine::detectTriggeredRules(
    const TransactionContext& txn,
    const NormalizedFeatures& features,
    const ModelPrediction& prediction)
{
    std::vector<std::string> rules;
    if (prediction.velocity_score >= 0.7) {
        rules.push_back("Velocity Threshold Exceeded");
    }
    if (features.velocity_1m >= 1.0) {
        rules.push_back("Rapid Burst Detected (>5 txns/min)");
    }
    if (features.device_risk > 0.5 && features.location_risk > 0.5) {
        rules.push_back("Impossible Travel");
    } else if (features.device_risk > 0.5) {
        rules.push_back("Unknown Device");
    } else if (features.location_risk > 0.5) {
        rules.push_back("New Location Detected");
    }
    if (prediction.amount_anomaly_score >= 0.7) {
        rules.push_back("Amount Anomaly Detected");
    }
    if (txn.amount >= HIGH_AMOUNT_LIMIT) {
        rules.push_back("High Value Transaction (>₹1L)");
    }
    if (features.amount_ratio > 5.0) {
        rules.push_back("Amount >5x User Average");
    }
    if (features.merchant_diversity >= 0.5 && features.velocity_1h >= 0.5) {
        rules.push_back("High Merchant Diversity");
    }
    if (txn.channel == "ATM" && txn.amount > 50000) {
        rules.push_back("Large ATM Withdrawal");
    }
    return rules;
}
double RuleEngine::applyRuleBoosts(
    double base_risk,
    const TransactionContext& txn,
    const NormalizedFeatures& features,
    const std::vector<std::string>& triggered_rules)
{
    double adjusted = base_risk;
    for (const auto& rule : triggered_rules) {
        if (rule == "Impossible Travel") {
            adjusted += IMPOSSIBLE_TRAVEL_BOOST;
        }
        else if (rule == "Unknown Device") {
            adjusted += UNKNOWN_DEVICE_BOOST;
        }
        else if (rule == "Rapid Burst Detected (>5 txns/min)") {
            adjusted = std::max(adjusted, 0.80);
        }
        else if (rule == "Large ATM Withdrawal") {
            adjusted += 0.05;
        }
    }
    if (txn.amount >= HIGH_AMOUNT_LIMIT &&
        features.device_risk > 0.5 &&
        features.location_risk > 0.5) {
        adjusted = std::max(adjusted, BLOCK_THRESHOLD);
    }
    return std::min(1.0, adjusted);
}
} 
