#pragma once
#include <string>
#include <vector>
#include <cmath>
#include <atomic>
#include "feature_engine.h"
namespace fraud {
struct ModelPrediction {
    double fraud_probability = 0.0;   
    double velocity_score = 0.0;      
    double amount_anomaly_score = 0.0; 
    double geo_anomaly_score = 0.0;   
    double ensemble_score = 0.0;      
    int64_t inference_time_us = 0;    
};
class FraudModel {
public:
    FraudModel();
    ModelPrediction predict(const NormalizedFeatures& features);
    std::string getVersion() const { return model_version_; }
    int64_t getTotalInferences() const { return total_inferences_.load(); }
private:
    double runEnsemble(double velocity, double amount_anomaly,
                       double geo_anomaly, const NormalizedFeatures& features);
    double computeWeightedRisk(double ensemble_score, double velocity,
                                double amount_anomaly, double geo_anomaly);
    double sigmoid(double x);
    std::string model_version_;
    std::atomic<int64_t> total_inferences_;
    static constexpr double W_ENSEMBLE = 0.40;
    static constexpr double W_VELOCITY = 0.25;
    static constexpr double W_GEO = 0.20;
    static constexpr double W_AMOUNT = 0.15;
};
} 
