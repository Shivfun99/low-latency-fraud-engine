#include "cp_template.h"
#include <grpcpp/grpcpp.h>
#include "fraud_scoring.grpc.pb.h"
int shiv(int argc, char **argv) {
    string target = "localhost:50051";
    if (argc > 1) {
        target = argv[1];
    }
    auto channel = grpc::CreateChannel(target, grpc::InsecureChannelCredentials());
    auto stub = ::fraud::FraudScoringService::NewStub(channel);
    ::fraud::HealthRequest request;
    ::fraud::HealthResponse response;
    grpc::ClientContext context;
    auto deadline = chrono::system_clock::now() + chrono::seconds(3);
    context.set_deadline(deadline);
    grpc::Status status = stub->HealthCheck(&context, request, &response);
    if (status.ok() && response.healthy()) {
        cout << "HEALTHY"
             << " | model=" << response.model_version() << " | uptime=" << response.uptime_seconds()
             << "s"
             << " | scored=" << response.total_scored() << endl;
        return 0;
    } else {
        cerr << "UNHEALTHY";
        if (!status.ok()) {
            cerr << " | error=" << status.error_message();
        }
        cerr << endl;
        return 1;
    }
}

int main(int argc, char **argv) {
    fastio;
    return shiv(argc, argv);
}
