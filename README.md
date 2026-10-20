# Low-Latency Fraud Engine

An event-driven fraud detection prototype built with Kafka, Redis, Spring Boot, C++ gRPC, PostgreSQL, Express/WebSocket, and React.

It accepts transactions, calculates recent user activity, scores fraud risk, stores decisions and cases, and streams alerts to an analyst dashboard. The C++ scorer is a hand-written heuristic ensemble; it is not a trained XGBoost model. Sub-20 ms latency and million-transaction throughput are design goals, not verified benchmarks.

Read [details.md](details.md) for the problem statement, what this project implements, how the components solve the problem, fixes, and verification results.

```text
REST transaction → Kafka transactions.raw → Spring orchestrator
                                            ├─ Redis rolling activity features
                                            ├─ C++ gRPC risk scoring
                                            └─ PostgreSQL transactions/decisions/cases
                                                     ↓
                                     Kafka fraud.decisions + fraud.alerts
                                            ├─ WebSocket → React dashboard
                                            └─ Notification inbox
```

## Run the complete stack with Docker

Install Docker with Compose, then run from the repository root:

```bash
docker compose up --build -d
docker compose ps
docker compose logs --tail=100
```

The first build downloads dependencies and can take several minutes. Compose builds the C++, Java, and JavaScript applications from source; prebuilt local images or JARs are not required. Kafka uses KRaft, so there is no ZooKeeper service.

Open **http://localhost:5173**. Use the dashboard transaction form to submit test transactions through the real scoring pipeline. Repeated requests for the same user exercise velocity detection. Cases can be reviewed and resolved from the dashboard.

```bash
# Stop containers, preserving PostgreSQL data
docker compose down
```

For host applications with containerized infrastructure, or a fully native installation, follow [run-without-docker.md](run-without-docker.md).

## Services

| Service              | Local port | Purpose                                      |
| -------------------- | ---------- | -------------------------------------------- |
| Kafka                | 9092       | Transaction and decision events              |
| Redis                | 6380       | Rolling features; container port is 6379     |
| PostgreSQL           | 5433       | Audit storage; container port is 5432        |
| C++ scorer           | 50051      | gRPC scoring and health RPC                  |
| Transaction gateway  | 8080       | Ingestion, demo login, sandbox               |
| Fraud orchestrator   | 8081       | Features, scoring, persistence, case actions |
| Case management      | 8082       | Case retrieval and actions                   |
| Notification service | 8083       | Recent alert inbox; optional dispatchers     |
| WebSocket gateway    | 3001       | Live alerts, decisions, counters             |
| Dashboard            | 5173       | React UI and API/WebSocket proxy             |

## Generate traffic and verify

With the services running:

```bash
npm --prefix simulator ci
node simulator/simulator.js --mode demo
node simulator/simulator.js --mode burst

npm --prefix websocket-gateway ci
node scripts/smoke-test.mjs
```

The smoke test creates seven transactions for a unique test user, checks authentication and validation, verifies live decisions and a blocked burst, retrieves a persisted case, resolves it, and checks the notification inbox. It leaves test records in the database.

The simulator uses the sandbox ingestion API, which still runs through Kafka, Redis, C++, and PostgreSQL. `/api/test-alert` on the WebSocket gateway creates a synthetic, non-actionable display alert only.

## Build and component tests

```bash
# C++: requires C++17, CMake, pkg-config, protobuf and gRPC development tools
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j2
ctest --test-dir build --output-on-failure

# Java: use JDK 17 and Maven
mvn package

# Optional Redis integration tests (against a running development Redis)
FRAUD_REDIS_TEST_PORT=6380 mvn -pl spring-services/fraud-orchestrator -am test

npm --prefix websocket-gateway ci
npm --prefix websocket-gateway test
npm --prefix simulator ci
npm --prefix simulator test
npm --prefix dashboard ci
npm --prefix dashboard run build
```

## API example

The sandbox endpoint is available without login for local development:

```bash
curl -fsS http://localhost:8080/api/v1/sandbox/ingest \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"rahul","amount":5000,"currency":"INR","merchant_id":"shop-1","location":"Chennai","device_id":"phone-1","channel":"ONLINE"}'
```

The response is `202 ACCEPTED`; scoring happens asynchronously. Use the dashboard, `/api/recent-decisions` on port 3001, or `/api/v1/cases` on port 8081 to inspect results. The authenticated `/api/v1/transactions` endpoint requires a JWT from `/api/v1/auth/login` using the local demo credentials `admin` / `password`.

## Current boundaries

This is a local development system. Demo credentials, public sandbox/case endpoints, plaintext service traffic, and the Vite development server are not a production security configuration. `BLOCK` records a fraud decision; no bank/payment processor is connected. Kafka/PostgreSQL delivery is not an exactly-once transaction, and the declared `fraud.dlq` topic does not yet have a configured dead-letter handler. See [details.md](details.md) for further limitations.
