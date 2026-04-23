package com.fraudshield.common.entity;
import com.fraudshield.common.enums.TransactionStatus;
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
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(length = 3)
    private String currency;
    @Column(name = "merchant_id", length = 64)
    private String merchantId;
    @Column(name = "merchant_name")
    private String merchantName;
    @Column(length = 64)
    private String category;
    @Column(name = "card_type", length = 32)
    private String cardType;
    @Column(length = 128)
    private String location;
    @Column(name = "device_id", length = 128)
    private String deviceId;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(length = 32)
    private String channel;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TransactionStatus status;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (currency == null) {
            currency = "INR";
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
