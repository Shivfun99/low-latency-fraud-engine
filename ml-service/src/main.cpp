#include <iostream>
#include <memory>
#include <string>
#include <csignal>
#include <grpcpp/grpcpp.h>
#include <grpcpp/ext/proto_server_reflection_plugin.h>
#include "fraud_scoring.grpc.pb.h"
#include "scoring_service.h"
using grpc::Server;
using grpc::ServerBuilder;
using grpc::ServerContext;
using grpc::Status;
class FraudScoringServiceImpl final : public ::fraud::FraudScoringService::Service {
public:
    FraudScoringServiceImpl() {
        std::cout << "[gRPC] FraudScoringService ready" << std::endl;
    }
    Status ScoreTransaction(ServerContext* context,
                            const ::fraud::ScoringRequest* request,
                            ::fraud::ScoringResponse* reply) override {
        fraud::TransactionContext txn;
        txn.transaction_id = request->transaction_id();
        txn.user_id = request->user_id();
        txn.amount = request->amount();
        txn.merchant_id = request->merchant_id();
        txn.category = request->category();
        txn.location = request->location();
        txn.device_id = request->device_id();
        txn.ip_address = request->ip_address();
        txn.channel = request->channel();
        txn.timestamp = request->timestamp();
        fraud::VelocityFeatures velocity;
        const auto& pf = request->features();
        velocity.txn_count_1m = pf.txn_count_1m();
        velocity.txn_count_10m = pf.txn_count_10m();
        velocity.txn_count_1h = pf.txn_count_1h();
        velocity.amount_sum_1h = pf.amount_sum_1h();
        velocity.avg_amount = pf.avg_amount();
        velocity.amount_deviation = pf.amount_deviation();
        velocity.is_known_device = pf.is_known_device();
        velocity.is_known_location = pf.is_known_location();
        velocity.unique_merchants_1h = pf.unique_merchants_1h();
        velocity.max_amount_24h = pf.max_amount_24h();
        auto result = scoring_service_.scoreTransaction(txn, velocity);
        reply->set_transaction_id(result.transaction_id);
        reply->set_risk_score(result.risk_score);
        reply->set_decision(result.decision);
        reply->set_velocity_score(result.velocity_score);
        reply->set_geo_anomaly_score(result.geo_anomaly_score);
        reply->set_amount_anomaly_score(result.amount_anomaly_score);
        reply->set_ml_model_score(result.ml_model_score);
        reply->set_processing_time_us(result.processing_time_us);
        for (const auto& rule : result.triggered_rules) {
            reply->add_triggered_rules(rule);
        }
        return Status::OK;
    }
    Status BatchScore(ServerContext* context,
                      const ::fraud::BatchScoringRequest* request,
                      ::fraud::BatchScoringResponse* reply) override {
        std::vector<std::pair<fraud::TransactionContext, fraud::VelocityFeatures>> batch;
        batch.reserve(request->requests_size());
        for (const auto& req : request->requests()) {
            fraud::TransactionContext txn;
            txn.transaction_id = req.transaction_id();
            txn.user_id = req.user_id();
            txn.amount = req.amount();
            txn.merchant_id = req.merchant_id();
            txn.category = req.category();
            txn.location = req.location();
            txn.device_id = req.device_id();
            txn.ip_address = req.ip_address();
            txn.channel = req.channel();
            txn.timestamp = req.timestamp();
            fraud::VelocityFeatures velocity;
            const auto& pf = req.features();
            velocity.txn_count_1m = pf.txn_count_1m();
            velocity.txn_count_10m = pf.txn_count_10m();
            velocity.txn_count_1h = pf.txn_count_1h();
            velocity.amount_sum_1h = pf.amount_sum_1h();
            velocity.avg_amount = pf.avg_amount();
            velocity.amount_deviation = pf.amount_deviation();
            velocity.is_known_device = pf.is_known_device();
            velocity.is_known_location = pf.is_known_location();
            velocity.unique_merchants_1h = pf.unique_merchants_1h();
            velocity.max_amount_24h = pf.max_amount_24h();
            batch.emplace_back(txn, velocity);
        }
        auto [results, total_us] = scoring_service_.batchScore(batch);
        for (const auto& result : results) {
            auto* resp = reply->add_responses();
            resp->set_transaction_id(result.transaction_id);
            resp->set_risk_score(result.risk_score);
            resp->set_decision(result.decision);
            resp->set_velocity_score(result.velocity_score);
            resp->set_geo_anomaly_score(result.geo_anomaly_score);
            resp->set_amount_anomaly_score(result.amount_anomaly_score);
            resp->set_ml_model_score(result.ml_model_score);
            resp->set_processing_time_us(result.processing_time_us);
            for (const auto& rule : result.triggered_rules) {
                resp->add_triggered_rules(rule);
            }
        }
        reply->set_total_processing_time_us(total_us);
        return Status::OK;
    }
    Status HealthCheck(ServerContext* context,
                       const ::fraud::HealthRequest* request,
                       ::fraud::HealthResponse* reply) override {
        reply->set_healthy(scoring_service_.isHealthy());
        reply->set_model_version(scoring_service_.getModelVersion());
        reply->set_uptime_seconds(scoring_service_.getUptimeSeconds());
        reply->set_total_scored(scoring_service_.getTotalScored());
        return Status::OK;
    }
private:
    fraud::ScoringService scoring_service_;
};
std::unique_ptr<Server> g_server;
void signalHandler(int signum) {
    std::cout << "\n[Server] Received signal " << signum
              << ", shutting down..." << std::endl;
    if (g_server) {
        g_server->Shutdown();
    }
}
void RunServer() {
    const char* port_env = std::getenv("ML_SERVICE_PORT");
    std::string port = port_env ? port_env : "50051";
    std::string server_address("0.0.0.0:" + port);
    FraudScoringServiceImpl service;
    grpc::reflection::InitProtoReflectionServerBuilderPlugin();
    ServerBuilder builder;
    builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);
    builder.SetMaxReceiveMessageSize(16 * 1024 * 1024);
    builder.SetMaxSendMessageSize(16 * 1024 * 1024);
    g_server = builder.BuildAndStart();
    std::cout << "════════════════════════════════════════════" << std::endl;
    std::cout << "  Fraud Scoring C++ ML Engine" << std::endl;
    std::cout << "  Listening: " << server_address << std::endl;
    std::cout << "  Pipeline:  FeatureEngine → FraudModel → RuleEngine" << std::endl;
    std::cout << "════════════════════════════════════════════" << std::endl;
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);
    g_server->Wait();
}
int main(int argc, char** argv) {
    RunServer();
    return 0;
}
