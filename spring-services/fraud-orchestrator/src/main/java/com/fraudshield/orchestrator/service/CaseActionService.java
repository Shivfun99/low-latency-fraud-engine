package com.fraudshield.orchestrator.service;
import com.fraudshield.common.dto.AnalystActionRequest;
import com.fraudshield.common.dto.CaseSummaryResponse;
import com.fraudshield.common.entity.AnalystActionEntity;
import com.fraudshield.common.entity.FraudAlertEntity;
import com.fraudshield.common.entity.FraudDecisionEntity;
import com.fraudshield.common.entity.TransactionEntity;
import com.fraudshield.common.enums.AlertStatus;
import com.fraudshield.orchestrator.repository.AnalystActionRepository;
import com.fraudshield.orchestrator.repository.FraudAlertRepository;
import com.fraudshield.orchestrator.repository.FraudDecisionRepository;
import com.fraudshield.orchestrator.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.FraudAlertEvent;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
@Service
public class CaseActionService {
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudDecisionRepository fraudDecisionRepository;
    private final TransactionRepository transactionRepository;
    private final AnalystActionRepository analystActionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String alertsTopic;
    public CaseActionService(FraudAlertRepository fraudAlertRepository,
                             FraudDecisionRepository fraudDecisionRepository,
                             TransactionRepository transactionRepository,
                             AnalystActionRepository analystActionRepository,
                             KafkaTemplate<String, String> kafkaTemplate,
                             ObjectMapper objectMapper,
                             @Value("${fraud.kafka.topics.alerts:fraud.alerts}") String alertsTopic) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.fraudDecisionRepository = fraudDecisionRepository;
        this.transactionRepository = transactionRepository;
        this.analystActionRepository = analystActionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.alertsTopic = alertsTopic;
    }
    @Transactional
    public CaseSummaryResponse applyAction(UUID transactionId, AnalystActionRequest request) {
        System.out.println("[CaseAction] Applying action " + request.action() + " for transaction: " + transactionId);
        List<FraudAlertEntity> alerts = fraudAlertRepository.findAllByTransactionId(transactionId);
        if (alerts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No cases found for transaction");
        }
        FraudDecisionEntity decision = fraudDecisionRepository.findTopByTransactionIdOrderByCreatedAtDesc(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision not found for transaction"));
        TransactionEntity transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        String action = request.action() == null ? "REVIEW" : request.action().trim().toUpperCase(Locale.ROOT);
        alerts.forEach(alert -> {
            alert.setAnalystId(defaultAnalyst(request.analystId()));
            alert.setStatus(resolveStatus(action));
            if (alert.getStatus() == AlertStatus.RESOLVED || alert.getStatus() == AlertStatus.DISMISSED) {
                alert.setResolvedAt(Instant.now());
            }
            fraudAlertRepository.save(alert);
        });
        FraudAlertEntity primaryAlert = alerts.get(0);
        AnalystActionEntity audit = new AnalystActionEntity();
        audit.setAlertId(primaryAlert.getId());
        audit.setAnalystId(defaultAnalyst(request.analystId()));
        audit.setAction(action);
        audit.setNotes(request.notes() == null || request.notes().isBlank()
                ? "Action " + action + " submitted from dashboard"
                : request.notes());
        analystActionRepository.save(audit);
        alerts.forEach(alert -> publishUpdate(alert, decision, transaction));
        return new CaseSummaryResponse(
                primaryAlert.getId(),
                decision.getId(),
                transaction.getId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getLocation(),
                decision.getRiskScore().doubleValue(),
                decision.getDecision(),
                primaryAlert.getSeverity(),
                primaryAlert.getStatus(),
                primaryAlert.getDescription(),
                primaryAlert.getCreatedAt(),
                primaryAlert.getResolvedAt()
        );
    }
    public List<CaseSummaryResponse> recentCases() {
        return fraudAlertRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(alert -> {
                    TransactionEntity transaction = transactionRepository.findById(alert.getTransactionId()).orElse(null);
                    FraudDecisionEntity decision = fraudDecisionRepository.findById(alert.getDecisionId()).orElse(null);
                    return new CaseSummaryResponse(
                            alert.getId(),
                            alert.getDecisionId(),
                            alert.getTransactionId(),
                            transaction == null ? "unknown" : transaction.getUserId(),
                            transaction == null ? null : transaction.getAmount(),
                            transaction == null ? null : transaction.getLocation(),
                            decision == null || decision.getRiskScore() == null ? 0.0 : decision.getRiskScore().doubleValue(),
                            decision == null ? null : decision.getDecision(),
                            alert.getSeverity(),
                            alert.getStatus(),
                            alert.getDescription(),
                            alert.getCreatedAt(),
                            alert.getResolvedAt()
                    );
                })
                .toList();
    }
    private AlertStatus resolveStatus(String action) {
        return switch (action) {
            case "APPROVE", "DISMISS" -> AlertStatus.DISMISSED;
            case "BLOCK", "RESOLVE" -> AlertStatus.RESOLVED;
            case "ESCALATE", "REVIEW" -> AlertStatus.INVESTIGATING;
            default -> AlertStatus.INVESTIGATING;
        };
    }
    private String defaultAnalyst(String analystId) {
        return analystId == null || analystId.isBlank() ? "dashboard-user" : analystId;
    }
    private void publishUpdate(FraudAlertEntity alert, FraudDecisionEntity decision, TransactionEntity transaction) {
        try {
            FraudAlertEvent event = new FraudAlertEvent(
                    alert.getId(),
                    transaction.getId(),
                    decision.getId(),
                    transaction.getUserId(),
                    transaction.getAmount(),
                    transaction.getLocation(),
                    decision.getRiskScore().doubleValue(),
                    decision.getDecision(),
                    decision.getVelocityScore() != null ? decision.getVelocityScore().doubleValue() : 0.0,
                    decision.getGeoAnomalyScore() != null ? decision.getGeoAnomalyScore().doubleValue() : 0.0,
                    decision.getAmountAnomalyScore() != null ? decision.getAmountAnomalyScore().doubleValue() : 0.0,
                    alert.getSeverity(),
                    alert.getStatus(),
                    true,
                    "analyst",
                    decision.getProcessingTimeMs() != null ? decision.getProcessingTimeMs() : 0,
                    alert.getDescription(),
                    decision.getRuleTriggered() != null ? java.util.Arrays.asList(decision.getRuleTriggered().split(",")) : java.util.Collections.emptyList(),
                    false, 
                    Instant.now()
            );
            kafkaTemplate.send(alertsTopic, transaction.getId().toString(), objectMapper.writeValueAsString(event));
            System.out.println("[CaseAction] Published update to Kafka topic " + alertsTopic + " for alert: " + alert.getId());
        } catch (JsonProcessingException e) {
            System.err.println("[CaseAction] Failed to publish alert update: " + e.getMessage());
        }
    }
}
