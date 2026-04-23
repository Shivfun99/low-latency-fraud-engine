package com.fraudshield.common.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fraudshield.common.enums.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TransactionEvent(
        UUID transactionId,
        String userId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantName,
        String category,
        String cardType,
        String location,
        String deviceId,
        String ipAddress,
        String channel,
        TransactionStatus status,
        Boolean isSandbox,
        Instant timestamp
) {
}
