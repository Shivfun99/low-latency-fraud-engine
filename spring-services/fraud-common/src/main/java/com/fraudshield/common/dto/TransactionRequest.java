package com.fraudshield.common.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TransactionRequest(
        @NotBlank String userId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency,
        String merchantId,
        String merchantName,
        String category,
        String cardType,
        String location,
        String deviceId,
        String ipAddress,
        String channel
) {
}
