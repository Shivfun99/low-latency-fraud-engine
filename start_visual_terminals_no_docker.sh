#!/bin/bash
echo "Starting Zookeeper..."
gnome-terminal --title="Zookeeper" -- bash -c "/home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/bin/zookeeper-server-start.sh /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/config/zookeeper.properties; exec bash"
sleep 2

echo "Starting Kafka..."
gnome-terminal --title="Kafka" -- bash -c "/home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/bin/kafka-server-start.sh /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/config/server.properties; exec bash"
sleep 2

echo "Starting Redis..."
gnome-terminal --title="Redis" -- bash -c "redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru; exec bash"

echo "Starting Postgres..."
sudo systemctl start postgresql

sleep 3

echo "Starting C++ ML Service..."
gnome-terminal --title="ML Service" -- bash -c "cd ml-service/build && ./fraud_scoring_server; exec bash"

echo "Starting Transaction Gateway..."
gnome-terminal --title="Transaction Gateway" -- bash -c "cd spring-services && java -jar transaction-gateway/target/transaction-gateway-1.0.0.jar; exec bash"

echo "Starting Fraud Orchestrator..."
gnome-terminal --title="Fraud Orchestrator" -- bash -c "cd spring-services && java -jar fraud-orchestrator/target/fraud-orchestrator-1.0.0.jar; exec bash"

echo "Starting Case Management..."
gnome-terminal --title="Case Management" -- bash -c "cd spring-services && java -jar case-management/target/case-management-1.0.0.jar; exec bash"

echo "Starting Notification Service..."
gnome-terminal --title="Notification Service" -- bash -c "cd spring-services && java -jar notification-service/target/notification-service-1.0.0.jar; exec bash"

echo "Starting WebSocket Gateway..."
gnome-terminal --title="WebSocket Gateway" -- bash -c "cd websocket-gateway && npm start; exec bash"

echo "Starting React Dashboard..."
gnome-terminal --title="Dashboard" -- bash -c "cd dashboard && npm run dev; exec bash"
