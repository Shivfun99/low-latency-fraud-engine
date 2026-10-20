#pragma once
#include "cp_template.h"
#include "feature_engine.h"
#include "fraud_model.h"
namespace fraud {
struct ScoringDecision {
    string decision;
    double final_risk_score = 0.0;
    vector<string> triggered_rules;
    string severity;
};
class RuleEngine {
  public:
    RuleEngine();
    ScoringDecision applyRules(const TransactionContext &txn, const NormalizedFeatures &features,
                               const ModelPrediction &prediction);
    string scoreToDecision(double risk_score);
    string scoreToSeverity(double risk_score);

  private:
    vector<string> detectTriggeredRules(const TransactionContext &txn,
                                        const NormalizedFeatures &features,
                                        const ModelPrediction &prediction);
    double applyRuleBoosts(double base_risk, const TransactionContext &txn,
                           const NormalizedFeatures &features,
                           const vector<string> &triggered_rules);
    static constexpr double BLOCK_THRESHOLD = 0.85;
    static constexpr double REVIEW_THRESHOLD = 0.55;
    static constexpr double HIGH_AMOUNT_LIMIT = 100000.0;
    static constexpr int RAPID_BURST_THRESHOLD = 5;
    static constexpr double UNKNOWN_DEVICE_LOCATION_BOOST = 0.25;
    static constexpr double UNKNOWN_DEVICE_BOOST = 0.10;
};
} // namespace fraud
