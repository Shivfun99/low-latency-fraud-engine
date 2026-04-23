#pragma once
#include <string>
#include <memory>
#include <atomic>
#include <chrono>
#include "feature_engine.h"
#include "fraud_model.h"
#include "rule_engine.h"
namespace fraud {
struct ScoringResult {
    std::string transaction_id;
    double risk_score = 0.0;
    std::string decision;                     
    double velocity_score = 0.0;
    double geo_anomaly_score = 0.0;
    double amount_anomaly_score = 0.0;
    double ml_model_score = 0.0;
    int64_t processing_time_us = 0;
    std::vector<std::string> triggered_rules;
};
class ScoringService {
public:
    ScoringService();
    ScoringResult scoreTransaction(
        const TransactionContext& txn,
        const VelocityFeatures& velocity
    );
    std::pair<std::vector<ScoringResult>, int64_t> batchScore(
        const std::vector<std::pair<TransactionContext, VelocityFeatures>>& batch
    );
    std::string getModelVersion() const;
    int64_t getTotalScored() const { return total_scored_.load(); }
    int64_t getUptimeSeconds() const;
    bool isHealthy() const { return healthy_; }
private:
    FeatureEngine feature_engine_;
    FraudModel fraud_model_;
    RuleEngine rule_engine_;
    std::atomic<int64_t> total_scored_;
    std::chrono::time_point<std::chrono::steady_clock> start_time_;
    bool healthy_;
};
} 
