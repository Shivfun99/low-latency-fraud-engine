#include "cp_template.h"
#include "rule_engine.h"

namespace fraud {
RuleEngine::RuleEngine() {
    cout << "[RuleEngine] Initialized with thresholds: "
         << "BLOCK>=" << BLOCK_THRESHOLD << " REVIEW>=" << REVIEW_THRESHOLD << endl;
}

ScoringDecision RuleEngine::applyRules(const TransactionContext &txn,
                                       const NormalizedFeatures &features,
                                       const ModelPrediction &prediction) {
    ScoringDecision decision;
    decision.triggered_rules = detectTriggeredRules(txn, features, prediction);
    double adjusted_risk =
        applyRuleBoosts(prediction.fraud_probability, txn, features, decision.triggered_rules);
    decision.final_risk_score = min(1.0, max(0.0, adjusted_risk));
    decision.decision = scoreToDecision(decision.final_risk_score);
    decision.severity = scoreToSeverity(decision.final_risk_score);
    return decision;
}

string RuleEngine::scoreToDecision(double risk_score) {
    if (risk_score >= BLOCK_THRESHOLD)
        return "BLOCK";
    if (risk_score >= REVIEW_THRESHOLD)
        return "REVIEW";
    return "ALLOW";
}

string RuleEngine::scoreToSeverity(double risk_score) {
    if (risk_score >= 0.90)
        return "CRITICAL";
    if (risk_score >= 0.70)
        return "HIGH";
    if (risk_score >= 0.50)
        return "MEDIUM";
    return "LOW";
}

vector<string> RuleEngine::detectTriggeredRules(const TransactionContext &txn,
                                                const NormalizedFeatures &features,
                                                const ModelPrediction &prediction) {
    vector<string> rules;
    if (prediction.velocity_score >= 0.7) {
        rules.push_back("Velocity Threshold Exceeded");
    }
    if (features.txn_count_1m > RAPID_BURST_THRESHOLD) {
        rules.push_back("Rapid Burst Detected (>5 txns/min)");
    }
    if (features.device_risk > 0.5 && features.location_risk > 0.5) {
        rules.push_back("Unknown Device + New Location");
    } else if (features.device_risk > 0.5) {
        rules.push_back("Unknown Device");
    } else if (features.location_risk > 0.5) {
        rules.push_back("New Location Detected");
    }
    if (prediction.amount_anomaly_score >= 0.7) {
        rules.push_back("Amount Anomaly Detected");
    }
    if (txn.amount >= HIGH_AMOUNT_LIMIT) {
        rules.push_back("High Value Transaction (>=₹1L)");
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

double RuleEngine::applyRuleBoosts(double base_risk, const TransactionContext &txn,
                                   const NormalizedFeatures &features,
                                   const vector<string> &triggered_rules) {
    double adjusted = base_risk;
    for (const auto &rule : triggered_rules) {
        if (rule == "Unknown Device + New Location") {
            adjusted += UNKNOWN_DEVICE_LOCATION_BOOST;
        } else if (rule == "Unknown Device") {
            adjusted += UNKNOWN_DEVICE_BOOST;
        } else if (rule == "Rapid Burst Detected (>5 txns/min)") {
            adjusted = max(adjusted, BLOCK_THRESHOLD);
        } else if (rule == "Large ATM Withdrawal") {
            adjusted += 0.05;
        }
    }
    if (txn.amount >= HIGH_AMOUNT_LIMIT && features.device_risk > 0.5 &&
        features.location_risk > 0.5) {
        adjusted = max(adjusted, BLOCK_THRESHOLD);
    }
    return min(1.0, adjusted);
}
} // namespace fraud
