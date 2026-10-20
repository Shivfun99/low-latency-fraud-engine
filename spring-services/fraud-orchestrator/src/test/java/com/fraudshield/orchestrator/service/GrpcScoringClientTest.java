package com.fraudshield.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.frauddetection.grpc.FraudScoringServiceGrpc;
import com.frauddetection.grpc.ScoringRequest;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.enums.DecisionType;
import com.fraudshield.common.enums.TransactionStatus;
import io.grpc.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GrpcScoringClientTest {

  private GrpcScoringClient unavailableClient() {
    var stub = mock(
      FraudScoringServiceGrpc.FraudScoringServiceBlockingStub.class
    );
    when(stub.withDeadlineAfter(2, TimeUnit.SECONDS)).thenReturn(stub);
    when(stub.scoreTransaction(any(ScoringRequest.class))).thenThrow(
      Status.UNAVAILABLE.asRuntimeException()
    );
    return new GrpcScoringClient(stub);
  }

  @Test
  void fallbackIdentifiesDegradedScoringAndBlocksTheSixthTransaction() {
    var client = unavailableClient();
    var five = client.score(event("100"), features(5, true));
    assertThat(five.decision()).isEqualTo(DecisionType.ALLOW);
    var six = client.score(event("100"), features(6, true));
    assertThat(six.decision()).isEqualTo(DecisionType.BLOCK);
    assertThat(six.riskScore()).isGreaterThanOrEqualTo(.85);
    assertThat(six.triggeredRules()).contains(
      "SCORING_FALLBACK",
      "VELOCITY_BURST"
    );
    assertThat(six.processingTimeMs()).isPositive();
    assertThat(six.mlScore()).isZero();
  }

  @Test
  void fallbackBlocksHighValueTransactionFromNewDeviceAndLocation() {
    var result = unavailableClient().score(event("100000"), features(1, false));
    assertThat(result.decision()).isEqualTo(DecisionType.BLOCK);
    assertThat(result.triggeredRules()).contains(
      "HIGH_AMOUNT",
      "NEW_DEVICE_LOCATION",
      "SCORING_FALLBACK"
    );
  }

  private FeatureEngineeringService.FeatureSnapshot features(
    int count,
    boolean known
  ) {
    return new FeatureEngineeringService.FeatureSnapshot(
      count,
      count,
      count,
      count * 100,
      100,
      1,
      known,
      known,
      1,
      100
    );
  }

  private TransactionEvent event(String amount) {
    return new TransactionEvent(
      UUID.randomUUID(),
      "user",
      new BigDecimal(amount),
      "INR",
      "merchant",
      "Shop",
      "retail",
      "credit",
      "Mumbai",
      "device",
      "127.0.0.1",
      "ONLINE",
      TransactionStatus.PENDING,
      false,
      Instant.now()
    );
  }
}
