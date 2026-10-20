#pragma once
#include "cp_template.h"
#include "feature_engine.h"
#include "fraud_model.h"
#include "rule_engine.h"
namespace fraud {
struct ScoringResult {
    string transaction_id;
    double risk_score = 0.0;
    string decision;
    double velocity_score = 0.0;
    double geo_anomaly_score = 0.0;
    double amount_anomaly_score = 0.0;
    double ml_model_score = 0.0;
    int64_t processing_time_us = 0;
    vector<string> triggered_rules;
};
class ScoringService {
  public:
    ScoringService();
    ScoringResult scoreTransaction(const TransactionContext &txn, const VelocityFeatures &velocity);
    pair<vector<ScoringResult>, int64_t>
    batchScore(const vector<pair<TransactionContext, VelocityFeatures>> &batch);
    string getModelVersion() const;
    int64_t getTotalScored() const { return total_scored_.load(); }
    int64_t getUptimeSeconds() const;
    bool isHealthy() const { return healthy_; }

  private:
    FeatureEngine feature_engine_;
    FraudModel fraud_model_;
    RuleEngine rule_engine_;
    atomic<int64_t> total_scored_;
    chrono::time_point<chrono::steady_clock> start_time_;
    bool healthy_;
};
} // namespace fraud
