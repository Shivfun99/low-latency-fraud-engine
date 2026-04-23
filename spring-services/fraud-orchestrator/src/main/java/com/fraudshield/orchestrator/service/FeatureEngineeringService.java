package com.fraudshield.orchestrator.service;
import com.fraudshield.common.dto.TransactionEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import java.time.Duration;
@Service
public class FeatureEngineeringService {
    public record FeatureSnapshot(
            int txnCount1m,
            int txnCount10m,
            int txnCount1h,
            double amountSum1h,
            double avgAmount,
            double amountDeviation,
            boolean knownDevice,
            boolean knownLocation,
            int uniqueMerchants1h,
            double maxAmount24h
    ) {
    }
    private final StringRedisTemplate redisTemplate;
    public FeatureEngineeringService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    public FeatureSnapshot buildFeatures(TransactionEvent event) {
        String userId = event.userId();
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        int txnCount1m = incrementCounter("txn_count:" + userId + ":1m", Duration.ofMinutes(2));
        int txnCount10m = incrementCounter("txn_count:" + userId + ":10m", Duration.ofMinutes(12));
        int txnCount1h = incrementCounter("txn_count:" + userId + ":1h", Duration.ofHours(2));
        double amountSum1h = incrementAmount("amount_sum:" + userId + ":1h", event.amount().doubleValue(), Duration.ofHours(2));
        String countKey = "amount_count:" + userId + ":24h";
        String sumKey = "amount_total:" + userId + ":24h";
        int totalCount = incrementCounter(countKey, Duration.ofHours(24));
        double totalAmount = incrementAmount(sumKey, event.amount().doubleValue(), Duration.ofHours(24));
        double avgAmount = totalCount == 0 ? event.amount().doubleValue() : totalAmount / totalCount;
        double amountDeviation = avgAmount <= 0.0 ? 1.0 : event.amount().doubleValue() / avgAmount;
        String deviceKey = "known_devices:" + userId;
        String locationKey = "known_locations:" + userId;
        String merchantKey = "merchants:" + userId + ":1h";
        boolean knownDevice = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(deviceKey, blankSafe(event.deviceId())));
        boolean knownLocation = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(locationKey, blankSafe(event.location())));
        redisTemplate.opsForSet().add(deviceKey, blankSafe(event.deviceId()));
        redisTemplate.opsForSet().add(locationKey, blankSafe(event.location()));
        redisTemplate.opsForSet().add(merchantKey, blankSafe(event.merchantId()));
        redisTemplate.expire(deviceKey, Duration.ofDays(7));
        redisTemplate.expire(locationKey, Duration.ofDays(7));
        redisTemplate.expire(merchantKey, Duration.ofHours(2));
        Long uniqueMerchants = redisTemplate.opsForSet().size(merchantKey);
        String maxAmountKey = "max_amount:" + userId + ":24h";
        double previousMax = parseDouble(values.get(maxAmountKey));
        double nextMax = Math.max(previousMax, event.amount().doubleValue());
        values.set(maxAmountKey, Double.toString(nextMax), Duration.ofHours(24));
        return new FeatureSnapshot(
                txnCount1m,
                txnCount10m,
                txnCount1h,
                amountSum1h,
                avgAmount,
                amountDeviation,
                knownDevice,
                knownLocation,
                uniqueMerchants == null ? 0 : uniqueMerchants.intValue(),
                nextMax
        );
    }
    private int incrementCounter(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, ttl);
        return value == null ? 0 : value.intValue();
    }
    private double incrementAmount(String key, double delta, Duration ttl) {
        Double value = redisTemplate.opsForValue().increment(key, delta);
        redisTemplate.expire(key, ttl);
        return value == null ? 0.0 : value;
    }
    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
    private String blankSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
