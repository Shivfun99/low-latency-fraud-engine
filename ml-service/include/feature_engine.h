#pragma once
#include <string>
#include <vector>
#include <map>
#include <cmath>
#include <algorithm>
namespace fraud {
struct VelocityFeatures {
    int txn_count_1m = 0;
    int txn_count_10m = 0;
    int txn_count_1h = 0;
    double amount_sum_1h = 0.0;
    double avg_amount = 0.0;
    double amount_deviation = 0.0;
    bool is_known_device = true;
    bool is_known_location = true;
    int unique_merchants_1h = 1;
    double max_amount_24h = 0.0;
};
struct NormalizedFeatures {
    double velocity_1m = 0.0;       
    double velocity_10m = 0.0;      
    double velocity_1h = 0.0;       
    double amount_ratio = 0.0;      
    double amount_zscore = 0.0;     
    double device_risk = 0.0;       
    double location_risk = 0.0;     
    double merchant_diversity = 0.0; 
    double amount_to_max_ratio = 0.0; 
    double combined_velocity = 0.0; 
};
struct TransactionContext {
    std::string transaction_id;
    std::string user_id;
    double amount = 0.0;
    std::string merchant_id;
    std::string category;
    std::string location;
    std::string device_id;
    std::string ip_address;
    std::string channel;
    int64_t timestamp = 0;
};
class FeatureEngine {
public:
    FeatureEngine();
    NormalizedFeatures extractFeatures(
        const TransactionContext& txn,
        const VelocityFeatures& velocity
    );
    double computeVelocityScore(const NormalizedFeatures& features);
    double computeAmountAnomalyScore(const NormalizedFeatures& features);
    double computeGeoAnomalyScore(const NormalizedFeatures& features);
private:
    double sigmoid(double x);
    double normalize(double value, double min_val, double max_val);
    static constexpr int VELOCITY_1M_HIGH = 5;
    static constexpr int VELOCITY_1M_MED = 3;
    static constexpr int VELOCITY_10M_HIGH = 10;
    static constexpr int VELOCITY_10M_MED = 5;
    static constexpr int VELOCITY_1H_HIGH = 20;
    static constexpr int VELOCITY_1H_MED = 10;
    static constexpr int MERCHANT_DIVERSITY_THRESHOLD = 5;
};
} 
