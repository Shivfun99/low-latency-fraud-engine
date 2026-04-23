#include "scoring_service.h"
#include <iostream>
#include <chrono>
namespace fraud {
ScoringService::ScoringService()
    : total_scored_(0), healthy_(true) {
    start_time_ = std::chrono::steady_clock::now();
    std::cout << "[ScoringService] Pipeline ready: "
              << "FeatureEngine → FraudModel → RuleEngine" << std::endl;
}
ScoringResult ScoringService::scoreTransaction(
    const TransactionContext& txn,
    const VelocityFeatures& velocity)
{
    auto start = std::chrono::high_resolution_clock::now();
    ScoringResult result;
    result.transaction_id = txn.transaction_id;
    NormalizedFeatures features = feature_engine_.extractFeatures(txn, velocity);
    ModelPrediction prediction = fraud_model_.predict(features);
    ScoringDecision decision = rule_engine_.applyRules(txn, features, prediction);
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);
    result.risk_score = decision.final_risk_score;
    result.decision = decision.decision;
    result.velocity_score = prediction.velocity_score;
    result.geo_anomaly_score = prediction.geo_anomaly_score;
    result.amount_anomaly_score = prediction.amount_anomaly_score;
    result.ml_model_score = prediction.ensemble_score;
    result.processing_time_us = duration.count();
    result.triggered_rules = decision.triggered_rules;
    total_scored_++;
    return result;
}
std::pair<std::vector<ScoringResult>, int64_t> ScoringService::batchScore(
    const std::vector<std::pair<TransactionContext, VelocityFeatures>>& batch)
{
    auto start = std::chrono::high_resolution_clock::now();
    std::vector<ScoringResult> results;
    results.reserve(batch.size());
    for (const auto& [txn, velocity] : batch) {
        results.push_back(scoreTransaction(txn, velocity));
    }
    auto end = std::chrono::high_resolution_clock::now();
    auto total_us = std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();
    return {results, total_us};
}
std::string ScoringService::getModelVersion() const {
    return fraud_model_.getVersion();
}
int64_t ScoringService::getUptimeSeconds() const {
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::seconds>(now - start_time_).count();
}
} 
