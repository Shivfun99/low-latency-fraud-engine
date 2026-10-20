#include "cp_template.h"
#include "fraud_model.h"
namespace fraud {
FraudModel::FraudModel() : model_version_("v1.0-ensemble-cpp"), total_inferences_(0) {
    cout << "[FraudModel] Loaded model " << model_version_ << endl;
}
ModelPrediction FraudModel::predict(const NormalizedFeatures &features) {
    auto start = chrono::steady_clock::now();
    ModelPrediction result;
    double velocity = features.combined_velocity;
    if (features.velocity_1m >= 0.6) {
        velocity = max(velocity, 0.7);
    }
    result.velocity_score = min(1.0, velocity);
    double amount_anomaly = 0.0;
    if (features.amount_ratio > 1.0) {
        amount_anomaly = sigmoid(2.0 * (features.amount_ratio - 3.0));
    }
    if (features.amount_to_max_ratio > 1.5) {
        amount_anomaly = min(1.0, amount_anomaly + 0.1);
    }
    result.amount_anomaly_score = min(1.0, max(0.0, amount_anomaly));
    double geo_anomaly = 0.0;
    geo_anomaly += features.device_risk * 0.4;
    geo_anomaly += features.location_risk * 0.4;
    if (features.device_risk > 0.5 && features.location_risk > 0.5) {
        geo_anomaly += 0.2;
    }
    result.geo_anomaly_score = min(1.0, geo_anomaly);
    result.ensemble_score = runEnsemble(result.velocity_score, result.amount_anomaly_score,
                                        result.geo_anomaly_score, features);
    result.fraud_probability =
        computeWeightedRisk(result.ensemble_score, result.velocity_score,
                            result.amount_anomaly_score, result.geo_anomaly_score);
    auto end = chrono::steady_clock::now();
    result.inference_time_us = chrono::duration_cast<chrono::microseconds>(end - start).count();
    total_inferences_++;
    return result;
}
double FraudModel::runEnsemble(double velocity, double amount_anomaly, double geo_anomaly,
                               const NormalizedFeatures &features) {
    double raw_score = 0.40 * velocity + 0.30 * amount_anomaly + 0.20 * geo_anomaly +
                       0.10 * features.merchant_diversity;
    double calibrated = sigmoid(4.0 * (raw_score - 0.3));
    if (velocity > 0.5 && geo_anomaly > 0.5) {
        calibrated = min(1.0, calibrated + 0.15);
    }
    if (velocity > 0.5 && amount_anomaly > 0.5) {
        calibrated = min(1.0, calibrated + 0.10);
    }
    if (geo_anomaly > 0.7 && amount_anomaly > 0.7) {
        calibrated = min(1.0, calibrated + 0.10);
    }
    return min(1.0, max(0.0, calibrated));
}
double FraudModel::computeWeightedRisk(double ensemble_score, double velocity,
                                       double amount_anomaly, double geo_anomaly) {
    double risk = W_ENSEMBLE * ensemble_score + W_VELOCITY * velocity + W_GEO * geo_anomaly +
                  W_AMOUNT * amount_anomaly;
    return min(1.0, max(0.0, risk));
}
double FraudModel::sigmoid(double x) { return 1.0 / (1.0 + exp(-x)); }
} // namespace fraud
