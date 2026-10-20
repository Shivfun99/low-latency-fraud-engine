#include "cp_template.h"
#include <grpcpp/grpcpp.h>
#include <grpcpp/ext/proto_server_reflection_plugin.h>
#include "grpc_service.h"

int shiv() {
    // Block termination signals before gRPC starts its worker threads. Handle
    // them synchronously below: gRPC Shutdown and logging are not signal-safe.
    sigset_t signals;
    sigemptyset(&signals);
    sigaddset(&signals, SIGINT);
    sigaddset(&signals, SIGTERM);
    if (pthread_sigmask(SIG_BLOCK, &signals, nullptr) != 0) {
        cerr << "[Server] Unable to configure shutdown signals" << endl;
        return 1;
    }

    const char *port_env = getenv("ML_SERVICE_PORT");
    const string port = port_env ? port_env : "50051";
    if (port.empty() || port.size() > 5 || port.find_first_not_of("0123456789") != string::npos ||
        stoi(port) < 1 || stoi(port) > 65535) {
        cerr << "[Server] ML_SERVICE_PORT must be between 1 and 65535" << endl;
        return 1;
    }

    const string address = "0.0.0.0:" + port;
    fraud::FraudScoringServiceImpl service;
    grpc::reflection::InitProtoReflectionServerBuilderPlugin();
    grpc::ServerBuilder builder;
    builder.AddListeningPort(address, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);
    builder.SetMaxReceiveMessageSize(16 * 1024 * 1024);
    builder.SetMaxSendMessageSize(16 * 1024 * 1024);
    auto server = builder.BuildAndStart();
    if (!server) {
        cerr << "[Server] Failed to listen on " << address << endl;
        return 1;
    }

    cout << "════════════════════════════════════════════" << endl;
    cout << "  Fraud Scoring C++ ML Engine" << endl;
    cout << "  Listening: " << address << endl;
    cout << "  Pipeline:  FeatureEngine → FraudModel → RuleEngine" << endl;
    cout << "════════════════════════════════════════════" << endl;

    int signal = 0;
    const int signal_result = sigwait(&signals, &signal);
    if (signal_result == 0) {
        cout << "[Server] Received signal " << signal << ", shutting down..." << endl;
    } else {
        cerr << "[Server] Unable to wait for shutdown signal" << endl;
    }
    server->Shutdown();
    server->Wait();
    return signal_result == 0 ? 0 : 1;
}

int main() {
    fastio;
    return shiv();
}
