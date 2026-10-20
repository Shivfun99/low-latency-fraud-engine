# Run applications on your machine

Run commands from the repository root. Use JDK 17, Maven, Node.js 20+, CMake, a C++17 compiler, pkg-config, protobuf compiler, gRPC C++ compiler plugin, and the protobuf/gRPC development libraries. Check your package manager for the packages appropriate to your OS.

## 1. Infrastructure

The easiest hybrid setup runs only infrastructure in Docker:

```bash
docker compose up -d kafka redis postgres kafka-init
docker compose ps
```

Kafka uses KRaft; do not start ZooKeeper. PostgreSQL is exposed on **5433**, Redis on **6380**, and Kafka on **9092**.

For a completely native setup, start your existing Kafka broker, Redis server, and PostgreSQL instance. Create a local development database and initialize its schema once:

```sql
CREATE USER fraud_user
WITH
  PASSWORD 'fraud_pass_2024';

CREATE DATABASE fraud_detection OWNER fraud_user;
```

```bash
psql -h localhost -p 5432 -U fraud_user -d fraud_detection -f infrastructure/db/init.sql
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fraud_detection
export SPRING_REDIS_PORT=6379
```

The database user may need permission to enable the `pgcrypto` extension. A freshly initialized Docker database already loads the schema automatically.

Set `KAFKA_HOME` to your Kafka installation, then create the topics:

```bash
: "${KAFKA_HOME:?Set KAFKA_HOME to your Kafka installation}"
for topic in transactions.raw fraud.decisions fraud.alerts fraud.dlq; do
  "$KAFKA_HOME/bin/kafka-topics.sh" --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic "$topic" --partitions 6 --replication-factor 1
done
```

## 2. Build applications

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j2
ctest --test-dir build --output-on-failure

# JAVA_HOME should point to your JDK 17 installation
mvn package
npm --prefix websocket-gateway ci
npm --prefix dashboard ci
npm --prefix dashboard run build
npm --prefix simulator ci
```

The root CMake project generates protobuf code and produces `build/fraud_scoring_server` and `build/health_check`. There is no separate `ml-service/CMakeLists.txt`.

## 3. Start applications

```bash
bash scripts/run-local.sh
```

The launcher starts the C++ scorer, four Java services, WebSocket gateway, and dashboard. It prints readiness, writes logs under `logs/`, and stops its child processes on Ctrl+C or a service failure. It does not start or stop infrastructure. Its default PostgreSQL URL uses port 5433 to match Compose; override `SPRING_DATASOURCE_URL` for a native database on 5432. Redis defaults to the project container port 6380; set `SPRING_REDIS_PORT=6379` for a native Redis.

Supported service connection variables include `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_BROKER`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `ML_HOST`, and `ML_PORT`. Use `JAVA_HOME` to select Java and `ML_BUILD_DIR` to select another CMake build directory.

Open **http://localhost:5173**. In another terminal:

```bash
node scripts/smoke-test.mjs
node simulator/simulator.js --mode demo
```

The legacy `start_visual_terminals_no_docker.sh` opens this launcher in a terminal, or runs it directly without a graphical desktop. `launch_all_background.sh` now starts the full Docker stack; it no longer depends on another developer's home directory.

## Checks and troubleshooting

```bash
build/health_check localhost:50051
curl -fsS http://localhost:8080/api/v1/health
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8083/actuator/health
curl -fsS http://localhost:3001/health
```

If a service exits, read its `logs/<service>.log`. A database connection failure usually means the database URL/port or credentials do not match the running instance. Kafka must advertise a broker address reachable by its clients: `localhost:9092` for host applications and `kafka:29092` inside Compose. A `202` ingestion response confirms publication, not completion of scoring; use the smoke test to verify the whole pipeline. Do not launch host applications and their Docker equivalents on the same ports simultaneously.
