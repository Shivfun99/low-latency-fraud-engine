package com.fraudshield.gateway.controller;
import com.fraudshield.common.dto.TransactionRequest;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.enums.TransactionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/sandbox")
@CrossOrigin(origins = "*")
public class SandboxController {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String transactionsTopic;
    public SandboxController(KafkaTemplate<String, String> kafkaTemplate,
                             ObjectMapper objectMapper,
                             @Value("${fraud.kafka.topics.transactions}") String transactionsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionsTopic = transactionsTopic;
    }
    @PostMapping("/ingest")
    public Map<String, Object> injectSandbox(@RequestBody TransactionRequest request,
                                            @RequestHeader(value = "X-Sandbox-Key", required = false) String apiKey) {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();
        TransactionEvent event = new TransactionEvent(
                transactionId,
                request.userId(),
                request.amount(),
                request.currency() == null || request.currency().isBlank() ? "USD" : request.currency(),
                request.merchantId(),
                request.merchantName(),
                request.category(),
                request.cardType(),
                request.location(),
                request.deviceId(),
                request.ipAddress(),
                request.channel() == null || request.channel().isBlank() ? "SANDBOX" : request.channel(),
                TransactionStatus.PENDING,
                true, 
                now
        );
        try {
            kafkaTemplate.send(transactionsTopic, transactionId.toString(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Serialization error", e);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_id", transactionId);
        response.put("status", "ACCEPTED");
        response.put("message", "Sandbox request accepted for processing");
        response.put("timestamp", now);
        return response;
    }
    @GetMapping("/status")
    public String status() {
        return "Sandbox Environment: ACTIVE (Mode: Interactive)";
    }
}
