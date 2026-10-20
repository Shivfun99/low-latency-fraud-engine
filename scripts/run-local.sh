#!/usr/bin/env bash
# Run built applications against existing Kafka/Redis/PostgreSQL.
# Ctrl+C stops only processes started by this script.
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
for tool in node curl; do
    command -v "$tool" >/dev/null || {
        printf 'Missing command: %s\n' "$tool" >&2
        exit 1
    }
done
if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
elif [[ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]]; then
    JAVA_BIN=/usr/lib/jvm/java-17-openjdk-amd64/bin/java
else
    JAVA_BIN="$(command -v java)"
fi
ML_BUILD_DIR="${ML_BUILD_DIR:-$PROJECT_DIR/build}"
[[ -x "$ML_BUILD_DIR/fraud_scoring_server" ]] || {
    echo 'Build C++ first: cmake -S . -B build && cmake --build build -j2' >&2
    exit 1
}
services=(transaction-gateway fraud-orchestrator case-management notification-service)
for service in "${services[@]}"; do
    [[ -f "spring-services/$service/target/$service-1.0.0.jar" ]] || {
        echo 'Build Java first: mvn package' >&2
        exit 1
    }
done
[[ -d websocket-gateway/node_modules && -f dashboard/node_modules/vite/bin/vite.js ]] || {
    echo 'Install packages first: npm --prefix websocket-gateway ci && npm --prefix dashboard ci' >&2
    exit 1
}
export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
export KAFKA_BROKER="${KAFKA_BROKER:-$KAFKA_BOOTSTRAP_SERVERS}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5433/fraud_detection}"
export SPRING_REDIS_HOST="${SPRING_REDIS_HOST:-localhost}"
export SPRING_REDIS_PORT="${SPRING_REDIS_PORT:-6380}"
export ML_HOST="${ML_HOST:-localhost}"
mkdir -p logs
pids=()
cleanup() {
    trap - EXIT
    for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
    for pid in "${pids[@]}"; do wait "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT
trap 'exit 0' INT TERM
start() {
    local name="$1"
    shift
    "$@" >"logs/$name.log" 2>&1 &
    pids+=("$!")
    printf 'Starting %s (log: logs/%s.log)\n' "$name" "$name"
}
start ml-service "$ML_BUILD_DIR/fraud_scoring_server"
for service in "${services[@]}"; do
    start "$service" "$JAVA_BIN" -Xms64m -Xmx256m \
        -jar "spring-services/$service/target/$service-1.0.0.jar"
done
start websocket-gateway node websocket-gateway/src/server.js
(
    cd dashboard
    exec node node_modules/vite/bin/vite.js --host 0.0.0.0 --strictPort
) >logs/dashboard.log 2>&1 &
pids+=("$!")
urls=(
    http://localhost:8080/api/v1/health
    http://localhost:8081/actuator/health
    http://localhost:8082/actuator/health
    http://localhost:8083/actuator/health
    http://localhost:3001/health
    http://localhost:5173
)
for attempt in {1..120}; do
    for pid in "${pids[@]}"; do
        kill -0 "$pid" 2>/dev/null || {
            echo 'A service exited; inspect logs/.' >&2
            exit 1
        }
    done
    ready=true
    for url in "${urls[@]}"; do
        curl --silent --fail --max-time 2 "$url" >/dev/null || ready=false
    done
    if "$ready"; then
        printf '\nReady: http://localhost:5173\nRun: node scripts/smoke-test.mjs\nCtrl+C stops the application services.\n'
        wait -n "${pids[@]}" || true
        echo 'A service stopped; shutting down the remaining application services.' >&2
        exit 1
    fi
    sleep 1
done
echo 'Startup timed out; inspect logs/ and verify infrastructure is running.' >&2
exit 1
