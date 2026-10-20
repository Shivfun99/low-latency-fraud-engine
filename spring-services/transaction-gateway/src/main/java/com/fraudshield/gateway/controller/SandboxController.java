package com.fraudshield.gateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.dto.TransactionRequest;
import com.fraudshield.common.enums.TransactionStatus;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sandbox")
@CrossOrigin(origins = "*")
public class SandboxController {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final String transactionsTopic;

  public SandboxController(
    KafkaTemplate<String, String> kafkaTemplate,
    ObjectMapper objectMapper,
    @Value("${fraud.kafka.topics.transactions}") String transactionsTopic
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.transactionsTopic = transactionsTopic;
  }

  @PostMapping("/ingest")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> injectSandbox(@Valid @RequestBody TransactionRequest request) {
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
      kafkaTemplate
        .send(transactionsTopic, event.userId(), objectMapper.writeValueAsString(event))
        .join();
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
