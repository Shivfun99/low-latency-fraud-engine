cmake_minimum_required(VERSION 3.16)
project(FraudScoringService CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ML_SERVICE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ml-service")

# ─── Find packages ──────────────────────────────────────
find_package(Threads REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(GRPCPP REQUIRED IMPORTED_TARGET grpc++)

# Proto file location
set(PROTO_DIR "${ML_SERVICE_DIR}/proto")
set(PROTO_FILE "${PROTO_DIR}/fraud_scoring.proto")

# Generated files
set(PROTO_SRC "${CMAKE_CURRENT_BINARY_DIR}/fraud_scoring.pb.cc")
set(PROTO_HDR "${CMAKE_CURRENT_BINARY_DIR}/fraud_scoring.pb.h")
set(GRPC_SRC "${CMAKE_CURRENT_BINARY_DIR}/fraud_scoring.grpc.pb.cc")
set(GRPC_HDR "${CMAKE_CURRENT_BINARY_DIR}/fraud_scoring.grpc.pb.h")

# Find protoc and grpc_cpp_plugin
find_program(PROTOC protoc REQUIRED)
find_program(GRPC_CPP_PLUGIN grpc_cpp_plugin
    HINTS /usr/bin /usr/local/bin)

if(NOT GRPC_CPP_PLUGIN)
    message(FATAL_ERROR "grpc_cpp_plugin not found. Install: sudo apt install -y protobuf-compiler-grpc")
endif()

# ─── Generate protobuf + gRPC sources ───────────────────
add_custom_command(
    OUTPUT "${PROTO_SRC}" "${PROTO_HDR}" "${GRPC_SRC}" "${GRPC_HDR}"
    COMMAND ${PROTOC}
    ARGS --grpc_out="${CMAKE_CURRENT_BINARY_DIR}"
         --cpp_out="${CMAKE_CURRENT_BINARY_DIR}"
         -I "${PROTO_DIR}"
         --plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN}
         "${PROTO_FILE}"
    DEPENDS "${PROTO_FILE}"
    COMMENT "Generating protobuf/gRPC C++ sources from fraud_scoring.proto..."
)

# ─── Main server executable ─────────────────────────────
add_executable(fraud_scoring_server
    "${ML_SERVICE_DIR}/src/main.cpp"
    "${ML_SERVICE_DIR}/src/feature_engine.cpp"
    "${ML_SERVICE_DIR}/src/fraud_model.cpp"
    "${ML_SERVICE_DIR}/src/rule_engine.cpp"
    "${ML_SERVICE_DIR}/src/scoring_service.cpp"
    ${PROTO_SRC}
    ${GRPC_SRC}
)

target_include_directories(fraud_scoring_server PRIVATE
    "${CMAKE_CURRENT_BINARY_DIR}"
    "${ML_SERVICE_DIR}/include"
)

target_link_libraries(fraud_scoring_server
    PRIVATE
    PkgConfig::GRPCPP
    grpc++_reflection
    protobuf
    Threads::Threads
)

# ─── Health check utility ────────────────────────────────
add_executable(health_check
    "${ML_SERVICE_DIR}/src/health_check.cpp"
    ${PROTO_SRC}
    ${GRPC_SRC}
)

target_include_directories(health_check PRIVATE
    "${CMAKE_CURRENT_BINARY_DIR}"
    "${ML_SERVICE_DIR}/include"
)

target_link_libraries(health_check
    PRIVATE
    PkgConfig::GRPCPP
    protobuf
    Threads::Threads
)
