package com.fraudshield.orchestrator.service;

import com.fraudshield.common.dto.TransactionEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

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
  ) {}

  private static final DefaultRedisScript<List> FEATURE_SCRIPT = featureScript();
  private final StringRedisTemplate redisTemplate;

  private static DefaultRedisScript<List> featureScript() {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("redis/rolling-features.lua"));
    script.setResultType(List.class);
    return script;
  }

  public FeatureEngineeringService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public FeatureSnapshot buildFeatures(TransactionEvent event) {
    return buildFeatures(event, Instant.now().toEpochMilli());
  }

  // Processing-time windows: one atomic script prevents concurrent users' requests losing updates.
  FeatureSnapshot buildFeatures(TransactionEvent event, long now) {
    String prefix = "features:{" + event.userId() + "}:";
    List<?> result = redisTemplate.execute(
      FEATURE_SCRIPT,
      List.of(
        prefix + "history",
        prefix + "amounts",
        prefix + "max",
        prefix + "totals",
        prefix + "hour",
        prefix + "merchants",
        prefix + "devices",
        prefix + "locations",
        prefix + "snapshots"
      ),
      Long.toString(now),
      event.transactionId().toString(),
      event.amount().toPlainString(),
      blankSafe(event.merchantId()),
      blankSafe(event.deviceId()),
      blankSafe(event.location())
    );
    if (result == null || result.size() != 10) {
      throw new IllegalStateException("Redis returned an invalid feature snapshot");
    }
    return new FeatureSnapshot(
      (int) number(result, 0),
      (int) number(result, 1),
      (int) number(result, 2),
      number(result, 3),
      number(result, 4),
      number(result, 5),
      number(result, 6) == 1,
      number(result, 7) == 1,
      (int) number(result, 8),
      number(result, 9)
    );
  }

  private double number(List<?> values, int index) {
    return Double.parseDouble(values.get(index).toString());
  }

  private String blankSafe(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}
