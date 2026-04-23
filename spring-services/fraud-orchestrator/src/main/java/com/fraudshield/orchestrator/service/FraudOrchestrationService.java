package com.fraudshield.orchestrator.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.common.dto.FraudDecisionEvent;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.entity.FraudAlertEntity;
import com.fraudshield.common.entity.FraudDecisionEntity;
import com.fraudshield.common.entity.TransactionEntity;
import com.fraudshield.common.enums.AlertSeverity;
import com.fraudshield.common.enums.AlertStatus;
import com.fraudshield.common.enums.DecisionType;
import com.fraudshield.common.enums.TransactionStatus;
import com.fraudshield.orchestrator.repository.FraudAlertRepository;
import com.fraudshield.orchestrator.repository.FraudDecisionRepository;
import com.fraudshield.orchestrator.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
@Service
public class FraudOrchestrationService {
    private final FeatureEngineeringService featureEngineeringService;
    private final GrpcScoringClient grpcScoringClient;
    private final TransactionRepository transactionRepository;
    private final FraudDecisionRepository fraudDecisionRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String decisionsTopic;
    private final String alertsTopic;
    public FraudOrchestrationService(FeatureEngineeringService featureEngineeringService,
                                     GrpcScoringClient grpcScoringClient,
                                     TransactionRepository transactionRepository,
                                     FraudDecisionRepository fraudDecisionRepository,
                                     FraudAlertRepository fraudAlertRepository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${fraud.kafka.topics.decisions}") String decisionsTopic,
                                     @Value("${fraud.kafka.topics.alerts}") String alertsTopic) {
        this.featureEngineeringService = featureEngineeringService;
        this.grpcScoringClient = grpcScoringClient;
        this.transactionRepository = transactionRepository;
        this.fraudDecisionRepository = fraudDecisionRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.decisionsTopic = decisionsTopic;
        this.alertsTopic = alertsTopic;
    }
    @Transactional
    public void process(TransactionEvent event) {
        FeatureEngineeringService.FeatureSnapshot features = featureEngineeringService.buildFeatures(event);
        ScoreResult score = grpcScoringClient.score(event, features);
        Instant processedAt = Instant.now();
        TransactionEntity transactionEntity = upsertTransaction(event, score.decision(), processedAt);
        FraudDecisionEntity decisionEntity = saveDecision(event, score);
        FraudDecisionEvent decisionEvent = new FraudDecisionEvent(
                decisionEntity.getId(),
                transactionEntity.getId(),
                event.userId(),
                event.amount(),
                event.location(),
                score.riskScore(),
                score.decision(),
                score.velocityScore(),
                score.geoAnomalyScore(),
                score.amountAnomalyScore(),
                score.mlScore(),
                score.processingTimeMs(),
                score.triggeredRules(),
                processedAt
        );
        publish(decisionsTopic, event.transactionId().toString(), decisionEvent);
        if (score.decision() != DecisionType.ALLOW) {
            FraudAlertEntity alertEntity = saveAlert(decisionEvent);
            FraudAlertEvent alertEvent = new FraudAlertEvent(
                    alertEntity.getId(),
                    event.transactionId(),
                    decisionEntity.getId(),
                    event.userId(),
                    event.amount(),
                    event.location(),
                    score.riskScore(),
                    score.decision(),
                    score.velocityScore(),
                    score.geoAnomalyScore(),
                    score.amountAnomalyScore(),
                    resolveSeverity(score.decision(), score.riskScore()),
                    alertEntity.getStatus(),
                    true,
                    "orchestrator",
                    score.processingTimeMs(),
                    alertEntity.getDescription(),
                    score.triggeredRules(),
                    event.isSandbox() != null && event.isSandbox(),
                    processedAt
            );
            publish(alertsTopic, event.transactionId().toString(), alertEvent);
        }
    }
    private TransactionEntity upsertTransaction(TransactionEvent event, DecisionType decision, Instant processedAt) {
        TransactionEntity entity = transactionRepository.findById(event.transactionId()).orElseGet(TransactionEntity::new);
        entity.setId(event.transactionId());
        entity.setUserId(event.userId());
        entity.setAmount(event.amount());
        entity.setCurrency(event.currency());
        entity.setMerchantId(event.merchantId());
        entity.setMerchantName(event.merchantName());
        entity.setCategory(event.category());
        entity.setCardType(event.cardType());
        entity.setLocation(event.location());
        entity.setDeviceId(event.deviceId());
        entity.setIpAddress(event.ipAddress());
        entity.setChannel(event.channel());
        entity.setStatus(TransactionStatus.valueOf(decision.name()));
        entity.setCreatedAt(event.timestamp());
        entity.setProcessedAt(processedAt);
        return transactionRepository.save(entity);
    }
    private FraudDecisionEntity saveDecision(TransactionEvent event, ScoreResult score) {
        FraudDecisionEntity entity = new FraudDecisionEntity();
        entity.setTransactionId(event.transactionId());
        entity.setRiskScore(decimal(score.riskScore()));
        entity.setDecision(score.decision());
        entity.setRuleTriggered(String.join(",", score.triggeredRules()));
        entity.setMlScore(decimal(score.mlScore()));
        entity.setVelocityScore(decimal(score.velocityScore()));
        entity.setGeoAnomalyScore(decimal(score.geoAnomalyScore()));
        entity.setAmountAnomalyScore(decimal(score.amountAnomalyScore()));
        entity.setProcessingTimeMs(score.processingTimeMs());
        return fraudDecisionRepository.save(entity);
    }
    private FraudAlertEntity saveAlert(FraudDecisionEvent decisionEvent) {
        FraudAlertEntity entity = new FraudAlertEntity();
        entity.setTransactionId(decisionEvent.transactionId());
        entity.setDecisionId(decisionEvent.decisionId());
        entity.setAlertType(decisionEvent.decision().name());
        entity.setSeverity(resolveSeverity(decisionEvent.decision(), decisionEvent.riskScore()));
        entity.setStatus(AlertStatus.OPEN);
        entity.setDescription(buildDescription(decisionEvent.decision(), decisionEvent.triggeredRules()));
        return fraudAlertRepository.save(entity);
    }
    private AlertSeverity resolveSeverity(DecisionType decision, double riskScore) {
        if (decision == DecisionType.BLOCK || riskScore >= 0.90) {
            return AlertSeverity.CRITICAL;
        }
        if (riskScore >= 0.75) {
            return AlertSeverity.HIGH;
        }
        if (riskScore >= 0.55) {
            return AlertSeverity.MEDIUM;
        }
        return AlertSeverity.LOW;
    }
    private String buildDescription(DecisionType decision, List<String> triggeredRules) {
        if (triggeredRules == null || triggeredRules.isEmpty()) {
            return "Decision " + decision + " generated from risk scoring pipeline.";
        }
        return "Decision " + decision + " triggered by " + String.join(", ", triggeredRules) + ".";
    }
    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
    private void publish(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbound event", exception);
        }
    }
}
