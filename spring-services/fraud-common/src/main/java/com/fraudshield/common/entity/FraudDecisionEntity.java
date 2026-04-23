package com.fraudshield.common.entity;
import com.fraudshield.common.enums.DecisionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "fraud_decisions")
public class FraudDecisionEntity {
    @Id
    private UUID id;
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;
    @Column(name = "risk_score", nullable = false, precision = 6, scale = 4)
    private BigDecimal riskScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DecisionType decision;
    @Column(name = "rule_triggered")
    private String ruleTriggered;
    @Column(name = "ml_score", precision = 6, scale = 4)
    private BigDecimal mlScore;
    @Column(name = "velocity_score", precision = 6, scale = 4)
    private BigDecimal velocityScore;
    @Column(name = "geo_anomaly_score", precision = 6, scale = 4)
    private BigDecimal geoAnomalyScore;
    @Column(name = "amount_anomaly_score", precision = 6, scale = 4)
    private BigDecimal amountAnomalyScore;
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;
    @Column(name = "created_at")
    private Instant createdAt;
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public DecisionType getDecision() { return decision; }
    public void setDecision(DecisionType decision) { this.decision = decision; }
    public String getRuleTriggered() { return ruleTriggered; }
    public void setRuleTriggered(String ruleTriggered) { this.ruleTriggered = ruleTriggered; }
    public BigDecimal getMlScore() { return mlScore; }
    public void setMlScore(BigDecimal mlScore) { this.mlScore = mlScore; }
    public BigDecimal getVelocityScore() { return velocityScore; }
    public void setVelocityScore(BigDecimal velocityScore) { this.velocityScore = velocityScore; }
    public BigDecimal getGeoAnomalyScore() { return geoAnomalyScore; }
    public void setGeoAnomalyScore(BigDecimal geoAnomalyScore) { this.geoAnomalyScore = geoAnomalyScore; }
    public BigDecimal getAmountAnomalyScore() { return amountAnomalyScore; }
    public void setAmountAnomalyScore(BigDecimal amountAnomalyScore) { this.amountAnomalyScore = amountAnomalyScore; }
    public Integer getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
