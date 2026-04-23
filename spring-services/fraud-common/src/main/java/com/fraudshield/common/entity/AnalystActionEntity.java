package com.fraudshield.common.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "analyst_actions")
public class AnalystActionEntity {
    @Id
    private UUID id;
    @Column(name = "alert_id", nullable = false)
    private UUID alertId;
    @Column(name = "analyst_id", nullable = false, length = 64)
    private String analystId;
    @Column(nullable = false, length = 32)
    private String action;
    @Column(columnDefinition = "TEXT")
    private String notes;
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
    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }
    public String getAnalystId() { return analystId; }
    public void setAnalystId(String analystId) { this.analystId = analystId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
