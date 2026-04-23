# Real Run Without Docker
> Instructions to run each component manually in separate terminals.

---

### Terminal 1: Zookeeper
```bash
/home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/bin/zookeeper-server-start.sh /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/config/zookeeper.properties
```

---

### Terminal 2: Kafka
```bash
/home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/bin/kafka-server-start.sh /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/config/server.properties
```

---

### Terminal 3: Redis
```bash
redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
```

---

### Terminal 4: PostgreSQL
```bash
sudo systemctl start postgresql
```

---

### Terminal 5: C++ ML gRPC Service
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/ml-service/build
./fraud_scoring_server
```

---

### Terminal 6: Transaction Gateway (Spring Boot)
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
java -jar transaction-gateway/target/transaction-gateway-1.0.0.jar
```

---

### Terminal 7: Fraud Orchestrator (Spring Boot)
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
java -jar fraud-orchestrator/target/fraud-orchestrator-1.0.0.jar
```

---

### Terminal 8: Case Management (Spring Boot)
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
java -jar case-management/target/case-management-1.0.0.jar
```

---

### Terminal 9: Notification Service (Spring Boot)
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services
java -jar notification-service/target/notification-service-1.0.0.jar
```

---

### Terminal 10: WebSocket Gateway (Node.js)
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/websocket-gateway
npm start
```

---

### Terminal 11: React Dashboard
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/dashboard
npm run dev
```

---

### Terminal 12: Simulator (Generate Load)
```bash
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/simulator
node simulator.js --mode demo
```

***

### ✅ Health Checks
Once running, you can verify they are active with these commands:

```bash
# Check Redis
redis-cli ping

# Check Transaction Gateway
curl http://localhost:8080/api/v1/health

# Check Fraud Orchestrator
curl http://localhost:8081/actuator/health

# Check Case Management
curl http://localhost:8082/actuator/health

# Check Notification Service
curl http://localhost:8083/actuator/health

# Check WebSocket Gateway
curl http://localhost:3001/health

# Check ML Service
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/ml-service/build
./health_check localhost:50051
```