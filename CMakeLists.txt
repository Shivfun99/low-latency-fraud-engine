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
set(PROTO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/proto")
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

# Generate and compile the shared protocol once, including in parallel builds.
add_library(fraud_protocol STATIC ${PROTO_SRC} ${GRPC_SRC})
target_include_directories(fraud_protocol PUBLIC "${CMAKE_CURRENT_BINARY_DIR}")
target_link_libraries(fraud_protocol PUBLIC PkgConfig::GRPCPP protobuf Threads::Threads)

add_library(fraud_scoring_core STATIC
    "${ML_SERVICE_DIR}/src/feature_engine.cpp"
    "${ML_SERVICE_DIR}/src/fraud_model.cpp"
    "${ML_SERVICE_DIR}/src/rule_engine.cpp"
    "${ML_SERVICE_DIR}/src/scoring_service.cpp"
)
target_include_directories(fraud_scoring_core PUBLIC "${ML_SERVICE_DIR}/include")

add_library(fraud_grpc_service STATIC
    "${ML_SERVICE_DIR}/src/grpc_service.cpp"
)
target_link_libraries(fraud_grpc_service PUBLIC fraud_scoring_core fraud_protocol)

# ─── Main server executable ─────────────────────────────
add_executable(fraud_scoring_server "${ML_SERVICE_DIR}/src/main.cpp")
target_link_libraries(fraud_scoring_server
    PRIVATE fraud_grpc_service grpc++_reflection
)

# ─── Health check utility ────────────────────────────────
add_executable(health_check "${ML_SERVICE_DIR}/src/health_check.cpp")
target_include_directories(health_check PRIVATE "${ML_SERVICE_DIR}/include")
target_link_libraries(health_check PRIVATE fraud_protocol)

include(CTest)
if(BUILD_TESTING)
    add_executable(scoring_tests "${ML_SERVICE_DIR}/tests/scoring_tests.cpp")
    target_link_libraries(scoring_tests PRIVATE fraud_grpc_service)
    add_test(NAME scoring_pipeline_and_grpc COMMAND scoring_tests)
    set_tests_properties(scoring_pipeline_and_grpc PROPERTIES TIMEOUT 20)
endif()
