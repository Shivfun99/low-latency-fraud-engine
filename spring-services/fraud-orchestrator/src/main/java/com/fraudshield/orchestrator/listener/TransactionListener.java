package com.fraudshield.orchestrator.listener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.orchestrator.service.FraudOrchestrationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class TransactionListener {
    private final ObjectMapper objectMapper;
    private final FraudOrchestrationService fraudOrchestrationService;
    public TransactionListener(ObjectMapper objectMapper, FraudOrchestrationService fraudOrchestrationService) {
        this.objectMapper = objectMapper;
        this.fraudOrchestrationService = fraudOrchestrationService;
    }
    @KafkaListener(topics = "${fraud.kafka.topics.transactions}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) throws JsonProcessingException {
        TransactionEvent event = objectMapper.readValue(payload, TransactionEvent.class);
        fraudOrchestrationService.process(event);
    }
}
