#include <iostream>
#include <memory>
#include <string>
#include <grpcpp/grpcpp.h>
#include "fraud_scoring.grpc.pb.h"
int main(int argc, char** argv) {
    std::string target = "localhost:50051";
    if (argc > 1) {
        target = argv[1];
    }
    auto channel = grpc::CreateChannel(target, grpc::InsecureChannelCredentials());
    auto stub = ::fraud::FraudScoringService::NewStub(channel);
    ::fraud::HealthRequest request;
    ::fraud::HealthResponse response;
    grpc::ClientContext context;
    auto deadline = std::chrono::system_clock::now() + std::chrono::seconds(3);
    context.set_deadline(deadline);
    grpc::Status status = stub->HealthCheck(&context, request, &response);
    if (status.ok() && response.healthy()) {
        std::cout << "HEALTHY"
                  << " | model=" << response.model_version()
                  << " | uptime=" << response.uptime_seconds() << "s"
                  << " | scored=" << response.total_scored()
                  << std::endl;
        return 0;
    } else {
        std::cerr << "UNHEALTHY";
        if (!status.ok()) {
            std::cerr << " | error=" << status.error_message();
        }
        std::cerr << std::endl;
        return 1;
    }
}
