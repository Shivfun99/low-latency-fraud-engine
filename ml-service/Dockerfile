# ═══════════════════════════════════════════════════════════
# Fraud Detection — C++ ML Service (Multi-stage Docker Build)
# ═══════════════════════════════════════════════════════════

# ── Stage 1: Build ──────────────────────────────────────
FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    pkg-config \
    libgrpc++-dev \
    protobuf-compiler-grpc \
    protobuf-compiler \
    libprotobuf-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy proto definitions and ml-service source
COPY proto/ /build/proto/
COPY ml-service/ /build/ml-service/
COPY CMakeLists.txt /build/CMakeLists.txt

# Build
WORKDIR /build
RUN mkdir -p build && cd build \
    && cmake -DCMAKE_BUILD_TYPE=Release .. \
    && make -j$(nproc)

# ── Stage 2: Runtime ────────────────────────────────────
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    libgrpc++1 \
    libprotobuf23 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy built binaries
COPY --from=builder /build/build/fraud_scoring_server /app/
COPY --from=builder /build/build/health_check /app/

# Make health check executable
RUN chmod +x /app/health_check /app/fraud_scoring_server

EXPOSE 50051

HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD ["/app/health_check", "localhost:50051"]

CMD ["./fraud_scoring_server"]
