#include "cp_template.h"
#include "grpc_service.h"

#include <grpcpp/grpcpp.h>

namespace {

void require(bool condition, const string &message) {
    if (!condition) {
        throw runtime_error(message);
    }
}

fraud::ScoringRequest makeRequest(const string &id) {
    fraud::ScoringRequest request;
    request.set_transaction_id(id);
    request.set_user_id("user-test");
    request.set_amount(100.0);
    request.set_channel("POS");
    auto *features = request.mutable_features();
    features->set_is_known_device(true);
    features->set_is_known_location(true);
    features->set_avg_amount(100.0);
    features->set_max_amount_24h(100.0);
    features->set_unique_merchants_1h(1);
    return request;
}

void setDeadline(grpc::ClientContext &context) {
    context.set_deadline(chrono::system_clock::now() + chrono::seconds(3));
}

void testThresholdsAndFeatures() {
    fraud::RuleEngine rules;
    require(rules.scoreToDecision(0.54999) == "ALLOW", "Below review threshold");
    require(rules.scoreToDecision(0.55) == "REVIEW", "Review boundary is inclusive");
    require(rules.scoreToDecision(0.84999) == "REVIEW", "Below block threshold");
    require(rules.scoreToDecision(0.85) == "BLOCK", "Block boundary is inclusive");

    fraud::FeatureEngine engine;
    fraud::TransactionContext txn;
    txn.amount = 100;
    fraud::VelocityFeatures velocity;
    velocity.txn_count_1m = 100;
    velocity.txn_count_10m = 100;
    velocity.txn_count_1h = 100;
    velocity.unique_merchants_1h = 100;
    const auto features = engine.extractFeatures(txn, velocity);
    require(features.combined_velocity == 1.0, "Burst velocity must be capped at one");
    require(features.merchant_diversity == 1.0, "Merchant diversity must be capped");
    require(features.amount_ratio == 1.0, "New user must not divide by zero");
}

void testGrpc(fraud::FraudScoringService::Stub &stub) {
    auto normal = makeRequest("normal");
    auto unfamiliar = makeRequest("unfamiliar");
    unfamiliar.mutable_features()->set_is_known_device(false);
    unfamiliar.mutable_features()->set_is_known_location(false);
    auto suspicious = makeRequest("suspicious");
    suspicious.set_amount(150000.0);
    suspicious.mutable_features()->set_is_known_device(false);
    suspicious.mutable_features()->set_is_known_location(false);

    vector<fraud::ScoringRequest> requests{normal, unfamiliar, suspicious};
    const vector<string> expected{"ALLOW", "REVIEW", "BLOCK"};
    vector<double> scores;
    for (size_t i = 0; i < requests.size(); ++i) {
        grpc::ClientContext context;
        setDeadline(context);
        fraud::ScoringResponse response;
        const auto status = stub.ScoreTransaction(&context, requests[i], &response);
        require(status.ok(), "Single scoring RPC failed: " + status.error_message());
        require(response.transaction_id() == requests[i].transaction_id(),
                "Response must preserve transaction ID");
        require(response.decision() == expected[i],
                "Expected " + expected[i] + " for " + requests[i].transaction_id());
        require(isfinite(response.risk_score()) && response.risk_score() >= 0.0 &&
                    response.risk_score() <= 1.0,
                "Score must be finite and bounded");
        require(response.processing_time_us() >= 0, "Timing must be nonnegative");
        if (i > 0) {
            require(response.triggered_rules_size() > 0, "Risk must include explanations");
        }
        scores.push_back(response.risk_score());
        cout << requests[i].transaction_id() << ": " << response.decision()
             << " risk=" << response.risk_score() << endl;
    }
    require(scores[0] < scores[1] && scores[1] < scores[2],
            "Risk must increase across normal, unfamiliar, and suspicious examples");

    for (int count : {5, 6}) {
        auto burst = makeRequest("burst-" + to_string(count));
        burst.mutable_features()->set_txn_count_1m(count);
        burst.mutable_features()->set_txn_count_10m(count);
        burst.mutable_features()->set_txn_count_1h(count);
        grpc::ClientContext context;
        setDeadline(context);
        fraud::ScoringResponse response;
        require(stub.ScoreTransaction(&context, burst, &response).ok(), "Burst scoring RPC failed");
        require(response.decision() == (count == 5 ? "ALLOW" : "BLOCK"),
                "Strict >5/min burst boundary failed");
        const bool has_burst_rule =
            find(response.triggered_rules().begin(), response.triggered_rules().end(),
                 "Rapid Burst Detected (>5 txns/min)") != response.triggered_rules().end();
        require(has_burst_rule == (count > 5), "Burst rule must match raw count");
        cout << burst.transaction_id() << ": " << response.decision()
             << " risk=" << response.risk_score() << endl;
    }

    fraud::BatchScoringRequest batch;
    for (const auto &request : requests) {
        *batch.add_requests() = request;
    }
    grpc::ClientContext batch_context;
    setDeadline(batch_context);
    fraud::BatchScoringResponse batch_response;
    require(stub.BatchScore(&batch_context, batch, &batch_response).ok(),
            "Batch scoring RPC failed");
    require(batch_response.responses_size() == 3, "Batch must return all responses");
    for (int i = 0; i < batch_response.responses_size(); ++i) {
        require(batch_response.responses(i).transaction_id() == requests[i].transaction_id(),
                "Batch response order must match request order");
        require(batch_response.responses(i).risk_score() == scores[i],
                "Batch and single scoring must agree");
    }

    auto invalid = normal;
    invalid.set_amount(numeric_limits<double>::quiet_NaN());
    auto negative = normal;
    negative.set_amount(-1.0);
    auto no_id = normal;
    no_id.clear_transaction_id();
    auto no_features = normal;
    no_features.clear_features();
    auto invalid_feature = normal;
    invalid_feature.mutable_features()->set_avg_amount(numeric_limits<double>::infinity());
    auto invalid_count = normal;
    invalid_count.mutable_features()->set_txn_count_1m(-1);
    for (const auto &request :
         {invalid, negative, no_id, no_features, invalid_feature, invalid_count}) {
        grpc::ClientContext context;
        setDeadline(context);
        fraud::ScoringResponse response;
        require(stub.ScoreTransaction(&context, request, &response).error_code() ==
                    grpc::StatusCode::INVALID_ARGUMENT,
                "Invalid input must be rejected before scoring");
    }

    *batch.add_requests() = invalid;
    grpc::ClientContext invalid_batch_context;
    setDeadline(invalid_batch_context);
    fraud::BatchScoringResponse invalid_batch_response;
    require(stub.BatchScore(&invalid_batch_context, batch, &invalid_batch_response).error_code() ==
                grpc::StatusCode::INVALID_ARGUMENT,
            "Invalid batch must be rejected");

    grpc::ClientContext health_context;
    setDeadline(health_context);
    fraud::HealthRequest health_request;
    fraud::HealthResponse health_response;
    require(stub.HealthCheck(&health_context, health_request, &health_response).ok(),
            "Health RPC failed");
    require(health_response.healthy(), "Service should be healthy");
    require(!health_response.model_version().empty(), "Health must report model version");
    require(health_response.total_scored() == 8,
            "Only successful single/batch requests should increment total_scored");
}

} // namespace

int shiv() {
    try {
        testThresholdsAndFeatures();
        fraud::FraudScoringServiceImpl service;
        grpc::ServerBuilder builder;
        int port = 0;
        builder.AddListeningPort("127.0.0.1:0", grpc::InsecureServerCredentials(), &port);
        builder.RegisterService(&service);
        auto server = builder.BuildAndStart();
        require(server != nullptr && port > 0, "Unable to start test gRPC server");
        auto channel =
            grpc::CreateChannel("127.0.0.1:" + to_string(port), grpc::InsecureChannelCredentials());
        auto stub = fraud::FraudScoringService::NewStub(channel);
        try {
            testGrpc(*stub);
        } catch (...) {
            server->Shutdown();
            throw;
        }
        server->Shutdown();
        server->Wait();
        cout << "All scoring and gRPC regression checks passed" << endl;
        return 0;
    } catch (const exception &error) {
        cerr << "FAIL: " << error.what() << endl;
        return 1;
    }
}

int main() {
    fastio;
    return shiv();
}
