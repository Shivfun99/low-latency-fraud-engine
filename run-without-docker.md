# 🚀 Run Fraud Detection System WITHOUT Docker — Step by Step

> Every command below runs natively on your machine. No Docker required.

---

## 📦 Step 0: Install All Dependencies

### Java 17 + Maven
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven
java --version    # verify: 17+
mvn --version     # verify: 3.8+
```

### Node.js 18+
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs
node --version    # verify: 18+
npm --version
```

### C++ Build Tools + gRPC
```bash
sudo apt install -y build-essential cmake pkg-config \
  libgrpc++-dev protobuf-compiler-grpc protobuf-compiler libprotobuf-dev
protoc --version          # verify installed
which grpc_cpp_plugin     # verify: /usr/bin/grpc_cpp_plugin
```

### Kafka (Download & Extract)
```bash
cd /opt
sudo wget https://downloads.apache.org/kafka/3.7.0/kafka_2.13-3.7.0.tgz
sudo tar -xzf kafka_2.13-3.7.0.tgz
sudo ln -s kafka_2.13-3.7.0 kafka

# Add to PATH (add to ~/.bashrc for permanent)
export KAFKA_HOME=/opt/kafka
export PATH=$PATH:$KAFKA_HOME/bin
```

### Redis
```bash
sudo apt install -y redis-server
```

### PostgreSQL
```bash
sudo apt install -y postgresql postgresql-client
```

---

## 🟢 Step 1: Start Zookeeper (Terminal 1)

```bash
# Using downloaded Kafka:
/opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties

# Keep this terminal open. Wait for:
# "INFO binding to port 0.0.0.0/0.0.0.0:2181"
```

---

## 🟢 Step 2: Start Kafka Broker (Terminal 2)

```bash
/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties

# Keep this terminal open. Wait for:
# "INFO [KafkaServer id=0] started"
```

---

## 🟢 Step 3: Create Kafka Topics (Terminal 3 — one-time)

```bash
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic transactions.raw \
  --partitions 6 --replication-factor 1

/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic fraud.decisions \
  --partitions 6 --replication-factor 1

/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic fraud.alerts \
  --partitions 3 --replication-factor 1

/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic fraud.dlq \
  --partitions 1 --replication-factor 1
```

### Verify topics were created:
```bash
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```
Expected output:
```
fraud.alerts
fraud.decisions
fraud.dlq
transactions.raw
```

---

## 🟢 Step 4: Start Redis (Terminal 3)

```bash
redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru

# Keep this terminal open. Wait for:
# "Ready to accept connections tcp"
```

### Verify Redis is running:
```bash
redis-cli ping
# Should print: PONG
```

---

## 🟢 Step 5: Start PostgreSQL & Setup Database (Terminal 4 — one-time)

```bash
# Start PostgreSQL if not running
sudo systemctl start postgresql
sudo systemctl status postgresql   # verify: active (running)

# Create user and database
sudo -u postgres psql -c "CREATE USER fraud_user WITH PASSWORD 'fraud_pass_2024';"
sudo -u postgres psql -c "CREATE DATABASE fraud_detection OWNER fraud_user;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE fraud_detection TO fraud_user;"

# Load the schema
sudo -u postgres psql -d fraud_detection -f /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/infrastructure/db/init.sql
```

### Verify database:
```bash
sudo -u postgres psql -d fraud_detection -c "\dt"
```
Expected: tables `transactions`, `fraud_decisions`, `fraud_alerts`, `analyst_actions`.

---

## 🟢 Step 6: Build & Start C++ ML Service (Terminal 4)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/ml-service

# Generate protobuf files (if not already done)
mkdir -p build && cd build
protoc \
  --grpc_out=. \
  --cpp_out=. \
  -I ../../proto \
  --plugin=protoc-gen-grpc=/usr/bin/grpc_cpp_plugin \
  ../../proto/fraud_scoring.proto

# Build with CMake
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)

# Start the ML server (port 50051)
./fraud_scoring_server

# Keep this terminal open. Wait for:
# "Fraud Scoring C++ ML Engine"
# "Listening: 0.0.0.0:50051"
```

### Verify ML service is running (from another terminal):
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/ml-service/build
./health_check localhost:50051
# Should print: HEALTHY | model=v1.0-ensemble-cpp | uptime=...
```

---

## 🟢 Step 7: Build Spring Boot Services (Terminal 5 — one-time build)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
mvn clean package -DskipTests

# Wait for: BUILD SUCCESS
# This creates two JAR files:
#   transaction-gateway/target/transaction-gateway-1.0.0.jar
#   fraud-orchestrator/target/fraud-orchestrator-1.0.0.jar
```

---

## 🟢 Step 8: Start Transaction Gateway (Terminal 5)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
java -jar transaction-gateway/target/transaction-gateway-1.0.0.jar

# Keep this terminal open. Wait for:
# "Started TransactionGatewayApplication in X seconds"
# Runs on port 8080
```

### Verify Gateway:
```bash
curl http://localhost:8080/api/v1/health
# Should return: {"status":"UP","service":"transaction-gateway",...}
```

---

## 🟢 Step 9: Start Fraud Orchestrator (Terminal 6)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
java -jar fraud-orchestrator/target/fraud-orchestrator-1.0.0.jar

# Keep this terminal open. Wait for:
# "Started FraudOrchestratorApplication in X seconds"
# Runs on port 8081
```

### Verify Orchestrator:
```bash
curl http://localhost:8081/actuator/health
# Should return: {"status":"UP"}
```

---

## 🟢 Step 10: Start WebSocket Gateway (Terminal 7)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/websocket-gateway
npm install
npm start

# Keep this terminal open. Wait for:
# "Fraud Detection — WebSocket Gateway"
# "HTTP:      http://localhost:3001"
# "WebSocket: ws://localhost:3001/ws"
```

### Verify WebSocket Gateway:
```bash
curl http://localhost:3001/health
# Should return: {"status":"UP","service":"websocket-gateway",...}
```

---

## 🟢 Step 11: Start React Dashboard (Terminal 8)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/dashboard
npm install
npm run dev

# Keep this terminal open. Wait for:
# "VITE ready in X ms"
# "Local: http://localhost:5173/"
```

### 👉 Open browser: http://localhost:5173

---

## 🟢 Step 12: Run Transaction Simulator (Terminal 9)

```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/simulator
npm install

# Choose a mode:

# Demo mode — Rahul's card fraud story (recommended first run)
node simulator.js --mode demo

# Burst mode — velocity attack simulation
node simulator.js --mode burst

# Fraud mode — 80% fraudulent transactions
node simulator.js --mode fraud

# Normal mode — continuous mixed traffic
node simulator.js
```

---

## 🧪 Quick Test Commands

### Send a normal transaction:
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "user_rahul_001",
    "amount": 2500,
    "merchant_id": "merchant_amazon",
    "merchant_name": "Amazon India",
    "category": "ECOMMERCE",
    "location": "Chennai",
    "device_id": "iPhone14_abc123",
    "ip_address": "103.21.58.100",
    "channel": "ONLINE"
  }'
```

### Send a suspicious transaction (should trigger REVIEW/BLOCK):
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "user_rahul_001",
    "amount": 150000,
    "merchant_id": "merchant_unknown",
    "merchant_name": "Unknown Store",
    "category": "ELECTRONICS",
    "location": "Moscow",
    "device_id": "Unknown_Device_999",
    "ip_address": "185.100.87.42",
    "channel": "POS"
  }'
```

### Send test alert directly (bypasses Kafka — tests WebSocket + Dashboard):
```bash
curl -X POST http://localhost:3001/api/test-alert
```

### View recent alerts and stats:
```bash
curl http://localhost:3001/api/stats
curl http://localhost:3001/api/recent-alerts
curl http://localhost:3001/api/recent-decisions
```

### Monitor Kafka topic in real-time:
```bash
/opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic fraud.alerts \
  --from-beginning
```

### Check Redis keys:
```bash
redis-cli keys "*"
redis-cli get "avg_amount:user_rahul_001"
```

### Query PostgreSQL:
```bash
sudo -u postgres psql -d fraud_detection \
  -c "SELECT transaction_id, user_id, amount, risk_score, decision FROM fraud_decisions ORDER BY created_at DESC LIMIT 10;"
```

---

## 🛑 Stop Everything

```bash
# Stop Spring Boot (find PIDs)
pkill -f "transaction-gateway-1.0.0.jar"
pkill -f "fraud-orchestrator-1.0.0.jar"

# Stop C++ ML Service
pkill -f "fraud_scoring_server"

# Stop WebSocket Gateway
pkill -f "node src/server.js"

# Stop Dashboard
pkill -f "vite"

# Stop Redis
redis-cli shutdown

# Stop Kafka (go to Terminal 2)
# Press Ctrl+C or:
/opt/kafka/bin/kafka-server-stop.sh

# Stop Zookeeper (go to Terminal 1)
# Press Ctrl+C or:
/opt/kafka/bin/zookeeper-server-stop.sh

# Stop PostgreSQL (optional)
sudo systemctl stop postgresql
```

---

## 📋 Port Summary

| Service              | Port  | URL / Command           |
|----------------------|-------|-------------------------|
| Zookeeper            | 2181  | —                       |
| Kafka Broker         | 9092  | —                       |
| Redis                | 6379  | `redis-cli`             |
| PostgreSQL           | 5432  | `psql -U fraud_user`    |
| C++ ML Service       | 50051 | `./health_check`        |
| Transaction Gateway  | 8080  | http://localhost:8080    |
| Fraud Orchestrator   | 8081  | http://localhost:8081    |
| WebSocket Gateway    | 3001  | http://localhost:3001    |
| React Dashboard      | 5173  | http://localhost:5173    |

---

## 📊 Data Flow (No Docker)

```
POST http://localhost:8080/api/v1/transactions
  │
  ▼
Transaction Gateway (Java, port 8080)
  │  validates → produces to Kafka
  ▼
Kafka [transactions.raw] (port 9092)
  │
  ▼
Fraud Orchestrator (Java, port 8081)
  │  ├── Redis velocity check (port 6379)
  │  ├── gRPC → C++ ML Service (port 50051)
  │  ├── Decision: ALLOW / REVIEW / BLOCK
  │  └── Save to PostgreSQL (port 5432)
  │
  ├── Kafka [fraud.decisions]
  └── Kafka [fraud.alerts] (if REVIEW/BLOCK)
        │
        ▼
  WebSocket Gateway (Node.js, port 3001)
        │  broadcasts via WebSocket
        ▼
  React Dashboard (port 5173) — Live UI
```

---

## ❓ Troubleshooting

### Kafka won't start?
```bash
# Check if port 9092 is already in use
sudo lsof -i :9092
# Kill the process if needed
sudo kill -9 <PID>
```

### Spring Boot can't connect to Kafka?
```bash
# Make sure Kafka is running and topics exist
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### C++ ML Service build fails?
```bash
# Make sure gRPC dev libs are installed
sudo apt install -y libgrpc++-dev protobuf-compiler-grpc protobuf-compiler libprotobuf-dev
# Check CMake version
cmake --version   # needs 3.16+
```

### PostgreSQL connection refused?
```bash
sudo systemctl start postgresql
sudo systemctl status postgresql
# Check if fraud_user exists
sudo -u postgres psql -c "\du"
```

### Redis connection refused?
```bash
redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru &
redis-cli ping   # PONG
```

### Dashboard won't start?
```bash
cd dashboard
rm -rf node_modules
npm install
npm run dev
```
