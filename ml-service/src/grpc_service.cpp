#include "cp_template.h"
#include "grpc_service.h"

namespace fraud {
namespace {

grpc::Status validateRequest(const ScoringRequest &request) {
    if (request.transaction_id().empty() || request.user_id().empty()) {
        return {grpc::StatusCode::INVALID_ARGUMENT, "transaction_id and user_id are required"};
    }
    if (!isfinite(request.amount()) || request.amount() <= 0.0) {
        return {grpc::StatusCode::INVALID_ARGUMENT, "amount must be a finite positive number"};
    }
    if (!request.has_features()) {
        return {grpc::StatusCode::INVALID_ARGUMENT, "enriched transaction features are required"};
    }
    const auto &features = request.features();
    if (features.txn_count_1m() < 0 || features.txn_count_10m() < 0 ||
        features.txn_count_1h() < 0 || features.unique_merchants_1h() < 0) {
        return {grpc::StatusCode::INVALID_ARGUMENT, "feature counts must be nonnegative"};
    }
    for (double value : {features.amount_sum_1h(), features.avg_amount(),
                         features.amount_deviation(), features.max_amount_24h()}) {
        if (!isfinite(value) || value < 0.0) {
            return {grpc::StatusCode::INVALID_ARGUMENT,
                    "amount features must be finite and nonnegative"};
        }
    }
    return grpc::Status::OK;
}

pair<TransactionContext, VelocityFeatures> convertRequest(const ScoringRequest &request) {
    TransactionContext txn;
    txn.transaction_id = request.transaction_id();
    txn.user_id = request.user_id();
    txn.amount = request.amount();
    txn.merchant_id = request.merchant_id();
    txn.category = request.category();
    txn.location = request.location();
    txn.device_id = request.device_id();
    txn.ip_address = request.ip_address();
    txn.channel = request.channel();
    txn.timestamp = request.timestamp();

    VelocityFeatures velocity;
    const auto &features = request.features();
    velocity.txn_count_1m = features.txn_count_1m();
    velocity.txn_count_10m = features.txn_count_10m();
    velocity.txn_count_1h = features.txn_count_1h();
    velocity.amount_sum_1h = features.amount_sum_1h();
    velocity.avg_amount = features.avg_amount();
    velocity.amount_deviation = features.amount_deviation();
    velocity.is_known_device = features.is_known_device();
    velocity.is_known_location = features.is_known_location();
    velocity.unique_merchants_1h = features.unique_merchants_1h();
    velocity.max_amount_24h = features.max_amount_24h();
    return {move(txn), velocity};
}

void fillResponse(const ScoringResult &result, ScoringResponse *reply) {
    reply->set_transaction_id(result.transaction_id);
    reply->set_risk_score(result.risk_score);
    reply->set_decision(result.decision);
    reply->set_velocity_score(result.velocity_score);
    reply->set_geo_anomaly_score(result.geo_anomaly_score);
    reply->set_amount_anomaly_score(result.amount_anomaly_score);
    reply->set_ml_model_score(result.ml_model_score);
    reply->set_processing_time_us(result.processing_time_us);
    for (const auto &rule : result.triggered_rules) {
        reply->add_triggered_rules(rule);
    }
}

} // namespace

grpc::Status FraudScoringServiceImpl::ScoreTransaction(grpc::ServerContext *,
                                                       const ScoringRequest *request,
                                                       ScoringResponse *reply) {
    const auto status = validateRequest(*request);
    if (!status.ok()) {
        return status;
    }
    const auto [txn, velocity] = convertRequest(*request);
    fillResponse(scoring_service_.scoreTransaction(txn, velocity), reply);
    return grpc::Status::OK;
}

grpc::Status FraudScoringServiceImpl::BatchScore(grpc::ServerContext *,
                                                 const BatchScoringRequest *request,
                                                 BatchScoringResponse *reply) {
    // Validate the entire batch before scoring so rejected batches do not
    // partially increment the service's transaction counters.
    vector<pair<TransactionContext, VelocityFeatures>> batch;
    batch.reserve(request->requests_size());
    for (const auto &item : request->requests()) {
        const auto status = validateRequest(item);
        if (!status.ok()) {
            return status;
        }
        batch.push_back(convertRequest(item));
    }
    const auto [results, total_us] = scoring_service_.batchScore(batch);
    for (const auto &result : results) {
        fillResponse(result, reply->add_responses());
    }
    reply->set_total_processing_time_us(total_us);
    return grpc::Status::OK;
}

grpc::Status FraudScoringServiceImpl::HealthCheck(grpc::ServerContext *, const HealthRequest *,
                                                  HealthResponse *reply) {
    reply->set_healthy(scoring_service_.isHealthy());
    reply->set_model_version(scoring_service_.getModelVersion());
    reply->set_uptime_seconds(scoring_service_.getUptimeSeconds());
    reply->set_total_scored(scoring_service_.getTotalScored());
    return grpc::Status::OK;
}

} // namespace fraud
