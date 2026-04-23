#pragma once
#include <string>
#include <vector>
#include "feature_engine.h"
#include "fraud_model.h"
namespace fraud {
struct ScoringDecision {
    std::string decision;                     
    double final_risk_score = 0.0;            
    std::vector<std::string> triggered_rules; 
    std::string severity;                     
};
class RuleEngine {
public:
    RuleEngine();
    ScoringDecision applyRules(
        const TransactionContext& txn,
        const NormalizedFeatures& features,
        const ModelPrediction& prediction
    );
    std::string scoreToDecision(double risk_score);
    std::string scoreToSeverity(double risk_score);
private:
    std::vector<std::string> detectTriggeredRules(
        const TransactionContext& txn,
        const NormalizedFeatures& features,
        const ModelPrediction& prediction
    );
    double applyRuleBoosts(
        double base_risk,
        const TransactionContext& txn,
        const NormalizedFeatures& features,
        const std::vector<std::string>& triggered_rules
    );
    static constexpr double BLOCK_THRESHOLD = 0.85;
    static constexpr double REVIEW_THRESHOLD = 0.55;
    static constexpr double HIGH_AMOUNT_LIMIT = 100000.0;   
    static constexpr int VELOCITY_HARD_LIMIT = 10;           
    static constexpr double IMPOSSIBLE_TRAVEL_BOOST = 0.25;
    static constexpr double UNKNOWN_DEVICE_BOOST = 0.10;
};
} 
