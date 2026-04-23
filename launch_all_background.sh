#!/bin/bash
mkdir -p logs

echo "Starting Zookeeper..."
nohup /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/bin/zookeeper-server-start.sh /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/config/zookeeper.properties > logs/zookeeper.log 2>&1 &
sleep 3

echo "Starting Kafka..."
nohup /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/bin/kafka-server-start.sh /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/opt/kafka_2.13-3.7.0/config/server.properties > logs/kafka.log 2>&1 &
sleep 5

echo "Starting ML Service..."
nohup /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/ml-service/build/fraud_scoring_server > logs/ml-service.log 2>&1 &

echo "Starting Spring Services..."
nohup java -jar /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services/transaction-gateway/target/transaction-gateway-1.0.0.jar > logs/tg.log 2>&1 &
nohup java -jar /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services/fraud-orchestrator/target/fraud-orchestrator-1.0.0.jar > logs/fo.log 2>&1 &
nohup java -jar /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services/case-management/target/case-management-1.0.0.jar > logs/cm.log 2>&1 &
nohup java -jar /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/spring-services/notification-service/target/notification-service-1.0.0.jar > logs/ns.log 2>&1 &

echo "Starting Node JS Services..."
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/websocket-gateway && nohup npm start > ../logs/ws.log 2>&1 &
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp/dashboard && nohup npm run dev > ../logs/ui.log 2>&1 &
cd /home/shiv-mishra/Fraud_Detection_Kakfa_cpp

echo "Done launching."
