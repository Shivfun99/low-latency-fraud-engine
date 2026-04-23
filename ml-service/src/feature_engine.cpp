#include "feature_engine.h"
#include <iostream>
#include <cmath>
namespace fraud {
FeatureEngine::FeatureEngine() {
    std::cout << "[FeatureEngine] Initialized" << std::endl;
}
NormalizedFeatures FeatureEngine::extractFeatures(
    const TransactionContext& txn,
    const VelocityFeatures& velocity)
{
    NormalizedFeatures nf;
    nf.velocity_1m = normalize(velocity.txn_count_1m, 0, VELOCITY_1M_HIGH);
    nf.velocity_10m = normalize(velocity.txn_count_10m, 0, VELOCITY_10M_HIGH);
    nf.velocity_1h = normalize(velocity.txn_count_1h, 0, VELOCITY_1H_HIGH);
    nf.combined_velocity = 0.5 * nf.velocity_1m +
                           0.3 * nf.velocity_10m +
                           0.2 * nf.velocity_1h;
    nf.combined_velocity = std::min(1.0, nf.combined_velocity);
    if (velocity.avg_amount > 0) {
        nf.amount_ratio = txn.amount / velocity.avg_amount;
        nf.amount_zscore = (txn.amount - velocity.avg_amount) /
                           std::max(velocity.amount_deviation, 1.0);
    } else {
        nf.amount_ratio = txn.amount > 10000 ? 3.0 : 1.0;
        nf.amount_zscore = txn.amount > 10000 ? 2.0 : 0.0;
    }
    if (velocity.max_amount_24h > 0) {
        nf.amount_to_max_ratio = txn.amount / velocity.max_amount_24h;
    } else {
        nf.amount_to_max_ratio = 1.0;
    }
    nf.device_risk = velocity.is_known_device ? 0.0 : 1.0;
    nf.location_risk = velocity.is_known_location ? 0.0 : 1.0;
    nf.merchant_diversity = normalize(
        velocity.unique_merchants_1h, 0, MERCHANT_DIVERSITY_THRESHOLD * 2
    );
    return nf;
}
double FeatureEngine::computeVelocityScore(const NormalizedFeatures& features) {
    double score = 0.0;
    if (features.velocity_1m >= 1.0) {
        score += 0.5;
    } else if (features.velocity_1m >= 0.6) {
        score += 0.4;
    } else if (features.velocity_1m > 0.2) {
        score += 0.1;
    }
    if (features.velocity_10m >= 1.0) {
        score += 0.3;
    } else if (features.velocity_10m >= 0.5) {
        score += 0.15;
    }
    if (features.velocity_1h >= 1.0) {
        score += 0.2;
    } else if (features.velocity_1h >= 0.5) {
        score += 0.1;
    }
    return std::min(score, 1.0);
}
double FeatureEngine::computeAmountAnomalyScore(const NormalizedFeatures& features) {
    if (features.amount_ratio <= 1.0) {
        return 0.0;
    }
    double anomaly = sigmoid(2.0 * (features.amount_ratio - 3.0));
    if (features.amount_to_max_ratio > 1.5) {
        anomaly = std::min(1.0, anomaly + 0.1);
    }
    return std::min(1.0, std::max(0.0, anomaly));
}
double FeatureEngine::computeGeoAnomalyScore(const NormalizedFeatures& features) {
    double score = 0.0;
    if (features.device_risk > 0.5) {
        score += 0.4;
    }
    if (features.location_risk > 0.5) {
        score += 0.4;
    }
    if (features.device_risk > 0.5 && features.location_risk > 0.5) {
        score += 0.2;
    }
    return std::min(score, 1.0);
}
double FeatureEngine::sigmoid(double x) {
    return 1.0 / (1.0 + std::exp(-x));
}
double FeatureEngine::normalize(double value, double min_val, double max_val) {
    if (max_val <= min_val) return 0.0;
    return std::min(1.0, std::max(0.0, (value - min_val) / (max_val - min_val)));
}
} 
