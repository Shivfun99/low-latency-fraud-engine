# # ⚡ Low-Latency Fraud Engine — Real-Time Fraud Detection System

> **Kafka** + **Redis** + **Spring Boot** + **C++ ML (gRPC)** + **Express.js WebSocket** + **React Dashboard**

A production-grade, event-driven fraud detection system capable of processing millions of transactions per second with **sub-20ms** end-to-end latency.

---

## 🏗️ Architecture

```
Transaction → Kafka → Spring Boot → Redis + C++ ML → Decision → Kafka → WebSocket → React
```

| Service | Tech | Port | Role |
|---------|------|------|------|
| Kafka | Confluent 7.5 | 9092 | Event streaming backbone |
| Redis | Redis 7 Alpine | 6379 | Real-time velocity detection |
| PostgreSQL | Postgres 16 | 5432 | Audit & persistence |
| C++ ML Service | C++17 / gRPC | 50051 | Low-latency fraud scoring (2-4ms) |
| Transaction Gateway | Spring Boot 3.2 | 8080 | REST ingestion → Kafka producer |
| Fraud Orchestrator | Spring Boot 3.2 | 8081 | Core fraud engine (consumer + scoring) |
| WebSocket Gateway | Express.js / ws | 3001 | Real-time alert broadcasting |
| Dashboard | React + Vite | 5173 | Live monitoring UI |

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for Spring Boot)
- Node.js 18+ (for WebSocket Gateway & Dashboard)
- CMake & gRPC (for C++ ML Service, or use Docker)

### 1. Start Infrastructure

```bash
docker-compose up -d zookeeper kafka redis postgres kafka-init
```

### 2. Start WebSocket Gateway

```bash
cd websocket-gateway
npm install
npm start
```

### 3. Start React Dashboard

```bash
cd dashboard
npm install
npm run dev
```

### 4. Run Transaction Simulator

```bash
cd simulator
npm install

# Normal mixed traffic
node simulator.js

# Velocity attack burst (Rahul scenario)
node simulator.js --mode burst

# Fraud-heavy traffic
node simulator.js --mode fraud

# Narrated demo
node simulator.js --mode demo
```

### 5. Start Spring Boot Services (optional, needs Kafka running)

```bash
cd spring-services
mvn clean package -DskipTests

# Transaction Gateway
java -jar transaction-gateway/target/transaction-gateway-1.0.0.jar

# Fraud Orchestrator
java -jar fraud-orchestrator/target/fraud-orchestrator-1.0.0.jar
```

### 6. Full Stack via Docker

```bash
docker-compose up --build
```

---

## 📡 Kafka Topics

| Topic | Purpose | Key |
|-------|---------|-----|
| `transactions.raw` | Incoming transactions | userId |
| `fraud.decisions` | All scored decisions | transactionId |
| `fraud.alerts` | High-risk alerts (BLOCK/REVIEW) | transactionId |
| `fraud.dlq` | Failed processing | transactionId |

---

## 🔴 Redis Velocity Engine

Sliding window counters with **Lua scripts** for atomic operations:

| Key Pattern | TTL | Purpose |
|-------------|-----|---------|
| `txn_count:{userId}:{window}` | 2m | 1-minute transaction count |
| `amount_sum:{userId}:{window}` | 1h | Hourly amount accumulator |
| `avg_amount:{userId}` | 24h | EWMA behavioral baseline |
| `known_devices:{userId}` | 7d | Device fingerprint tracking |
| `known_locations:{userId}` | 7d | Location history |
| `merchants:{userId}:{window}` | 1h | Unique merchant tracking |

---

## 🤖 C++ ML Service

The ML service uses a **simulated XGBoost ensemble** (5 decision trees) with:
- **Feature engineering**: sigmoid normalization, log transforms, interaction features
- **Rule engine**: 5 pre-filter rules (velocity burst, amount threshold, device/location, cumulative, merchant hopping)
- **Decision logic**: ALLOW (<55%), REVIEW (55-85%), BLOCK (>85%)
- **Latency target**: 2-4ms per transaction

---

## 📊 Fraud Rules

| Rule | Trigger | Action |
|------|---------|--------|
| Velocity Burst | >5 txns in 1 minute | BLOCK |
| High Amount | Single txn > ₹100,000 | REVIEW |
| Amount Spike | Transaction > 5x user average | REVIEW |
| New Device + Location | Unknown device AND location | REVIEW |
| Merchant Hopping | >8 unique merchants in 1 hour | BLOCK |
| Cumulative High | 1h total > ₹200,000 | REVIEW |

---

## ⚡ Latency Budget

| Stage | Target |
|-------|--------|
| Kafka ingest | ≤2ms |
| Redis velocity | ≤1ms |
| C++ ML inference | ≤4ms |
| Decision logic | ≤2ms |
| WebSocket push | ≤3ms |
| UI render | ≤3ms |
| **Total** | **≤18ms** |

---

## 🧪 Testing Scenarios

### Rahul's Card Clone (Demo Mode)
```
node simulator.js --mode demo
```

Simulates the exact scenario from the problem statement:
1. Rahul makes normal purchase in Chennai
2. Card used in Delhi 30 seconds later
3. Rapid transactions: ₹5K → ₹10K → ₹25K → ₹40K
4. System blocks within milliseconds

---

## 📁 Project Structure

```
Fraud_Detection_Kafka_cpp/
├── docker-compose.yml           # Full stack orchestration
├── proto/
│   └── fraud_scoring.proto      # Shared gRPC definition
├── ml-service/                  # C++ ML gRPC Service
│   ├── CMakeLists.txt
│   ├── Dockerfile
│   ├── include/                 # Headers
│   └── src/                     # Implementation
├── spring-services/             # Java Spring Boot
│   ├── pom.xml                  # Parent POM
│   ├── transaction-gateway/     # REST → Kafka
│   └── fraud-orchestrator/      # Core fraud engine
├── websocket-gateway/           # Express.js + WebSocket
│   └── src/server.js
├── dashboard/                   # React + Vite
│   └── src/
│       ├── App.jsx
│       ├── index.css
│       └── hooks/useWebSocket.js
├── simulator/                   # Transaction simulator
│   └── simulator.js
└── infrastructure/
    └── db/init.sql             # PostgreSQL schema
```

---

## 🔐 Security Considerations

- Service-to-service authentication (mTLS for gRPC)
- Data encryption at rest (PostgreSQL) and in transit
- Role-based access for dashboard
- Idempotency via transaction IDs
- 
#####
Kafka, Redis, and Docker are runtime dependencies and are not committed to the repository.
Kafka binaries under /opt are for local development only.
Use docker-compose.yml or install services manually.
## 📈 Scalability

- **Kafka**: Increase partitions (currently 6)
- **Spring Boot**: Scale horizontally with consumer groups
- **Redis**: Redis Cluster for sharding
- **ML Service**: Load-balanced gRPC with multiple replicas

---

Built with ❤️ for real-time fraud detection at scale.
