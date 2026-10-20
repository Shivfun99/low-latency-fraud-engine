#include "cp_template.h"
#include "scoring_service.h"

namespace fraud {
ScoringService::ScoringService() : total_scored_(0), healthy_(true) {
    start_time_ = chrono::steady_clock::now();
    cout << "[ScoringService] Pipeline ready: "
         << "FeatureEngine → FraudModel → RuleEngine" << endl;
}

ScoringResult ScoringService::scoreTransaction(const TransactionContext &txn,
                                               const VelocityFeatures &velocity) {
    auto start = chrono::steady_clock::now();
    ScoringResult result;
    result.transaction_id = txn.transaction_id;
    NormalizedFeatures features = feature_engine_.extractFeatures(txn, velocity);
    ModelPrediction prediction = fraud_model_.predict(features);
    ScoringDecision decision = rule_engine_.applyRules(txn, features, prediction);
    auto end = chrono::steady_clock::now();
    auto duration = chrono::duration_cast<chrono::microseconds>(end - start);
    result.risk_score = decision.final_risk_score;
    result.decision = decision.decision;
    result.velocity_score = prediction.velocity_score;
    result.geo_anomaly_score = prediction.geo_anomaly_score;
    result.amount_anomaly_score = prediction.amount_anomaly_score;
    result.ml_model_score = prediction.ensemble_score;
    result.processing_time_us = duration.count();
    result.triggered_rules = std::move(decision.triggered_rules);
    total_scored_++;
    return result;
}

pair<vector<ScoringResult>, int64_t>
ScoringService::batchScore(const vector<pair<TransactionContext, VelocityFeatures>> &batch) {
    auto start = chrono::steady_clock::now();
    vector<ScoringResult> results;
    results.reserve(batch.size());
    for (const auto &[txn, velocity] : batch) {
        results.push_back(scoreTransaction(txn, velocity));
    }
    auto end = chrono::steady_clock::now();
    auto total_us = chrono::duration_cast<chrono::microseconds>(end - start).count();
    return {results, total_us};
}

string ScoringService::getModelVersion() const { return fraud_model_.getVersion(); }

int64_t ScoringService::getUptimeSeconds() const {
    auto now = chrono::steady_clock::now();
    return chrono::duration_cast<chrono::seconds>(now - start_time_).count();
}
} // namespace fraud
