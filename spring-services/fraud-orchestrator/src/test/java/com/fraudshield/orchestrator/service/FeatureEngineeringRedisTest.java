package com.fraudshield.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.enums.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "FRAUD_REDIS_TEST_PORT", matches = "\\d+")
class FeatureEngineeringRedisTest {

  private LettuceConnectionFactory connection;
  private StringRedisTemplate redis;
  private FeatureEngineeringService service;
  private String user;
  private static final long NOW = 1_700_000_000_000L;

  @BeforeEach
  void connect() {
    connection = new LettuceConnectionFactory(
      "localhost",
      Integer.parseInt(System.getenv("FRAUD_REDIS_TEST_PORT"))
    );
    connection.afterPropertiesSet();
    redis = new StringRedisTemplate(connection);
    service = new FeatureEngineeringService(redis);
    user = "feature-test-" + UUID.randomUUID();
  }

  @AfterEach
  void close() {
    var keys = redis.keys("features:{" + user + "}:*");
    if (keys != null && !keys.isEmpty()) redis.delete(keys);
    connection.destroy();
  }

  @Test
  void rollingWindowsExpireWhileTrafficContinues() {
    service.buildFeatures(event("100", "merchant-a"), NOW);
    var minute = service.buildFeatures(event("200", "merchant-b"), NOW + 60_001);
    assertThat(minute.txnCount1m()).isEqualTo(1);
    assertThat(minute.txnCount10m()).isEqualTo(2);
    assertThat(minute.amountSum1h()).isEqualTo(300);
    var tenMinutes = service.buildFeatures(event("300", "merchant-c"), NOW + 600_001);
    assertThat(tenMinutes.txnCount10m()).isEqualTo(2);
    var hour = service.buildFeatures(event("50", "merchant-d"), NOW + 3_600_001);
    assertThat(hour.txnCount1h()).isEqualTo(3);
    assertThat(hour.amountSum1h()).isEqualTo(550);
    assertThat(hour.uniqueMerchants1h()).isEqualTo(3);
  }

  @Test
  void spikeUsesPriorAverageAndDuplicateDoesNotChangeFeatures() {
    service.buildFeatures(event("100", "merchant"), NOW);
    var spikeEvent = event("10000", "merchant");
    var spike = service.buildFeatures(spikeEvent, NOW + 1);
    assertThat(spike.avgAmount()).isEqualTo(100);
    assertThat(spike.amountDeviation()).isEqualTo(100);
    assertThat(spike.knownDevice()).isTrue();
    assertThat(service.buildFeatures(spikeEvent, NOW + 2)).isEqualTo(spike);
    assertThat(service.buildFeatures(event("100", "merchant"), NOW + 3).txnCount1m()).isEqualTo(3);
  }

  @Test
  void dailyAverageAndMaximumExpire() {
    service.buildFeatures(event("10000", "merchant-a"), NOW);
    service.buildFeatures(event("100", "merchant-b"), NOW + 1_000);
    var nextDay = service.buildFeatures(event("50", "merchant-c"), NOW + 86_400_001);
    assertThat(nextDay.avgAmount()).isEqualTo(100);
    assertThat(nextDay.maxAmount24h()).isEqualTo(100);
    assertThat(nextDay.txnCount1h()).isEqualTo(1);
    assertThat(nextDay.amountSum1h()).isEqualTo(50);
  }

  private TransactionEvent event(String amount, String merchant) {
    return new TransactionEvent(
      UUID.randomUUID(),
      user,
      new BigDecimal(amount),
      "INR",
      merchant,
      merchant,
      "retail",
      "credit",
      "Mumbai",
      "device",
      "127.0.0.1",
      "SANDBOX",
      TransactionStatus.PENDING,
      true,
      Instant.ofEpochMilli(NOW)
    );
  }
}
