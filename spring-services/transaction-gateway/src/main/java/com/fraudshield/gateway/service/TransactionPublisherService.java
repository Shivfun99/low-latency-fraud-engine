package com.fraudshield.gateway.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.dto.TransactionRequest;
import com.fraudshield.common.enums.TransactionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
@Service
public class TransactionPublisherService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String transactionsTopic;
    public TransactionPublisherService(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${fraud.kafka.topics.transactions}") String transactionsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionsTopic = transactionsTopic;
    }
    public Map<String, Object> publish(TransactionRequest request) {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();
        TransactionEvent event = new TransactionEvent(
                transactionId,
                request.userId(),
                request.amount(),
                request.currency() == null || request.currency().isBlank() ? "INR" : request.currency(),
                request.merchantId(),
                request.merchantName(),
                request.category(),
                request.cardType(),
                request.location(),
                request.deviceId(),
                request.ipAddress(),
                request.channel() == null || request.channel().isBlank() ? "ONLINE" : request.channel(),
                TransactionStatus.PENDING,
                false, 
                now
        );
        try {
            kafkaTemplate.send(transactionsTopic, event.userId(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize transaction payload", exception);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ACCEPTED");
        response.put("transaction_id", transactionId);
        response.put("decision", "PENDING");
        response.put("topic", transactionsTopic);
        response.put("timestamp", now);
        return response;
    }
}
