package com.fraudshield.orchestrator.service;

import com.frauddetection.grpc.FraudScoringServiceGrpc;
import com.frauddetection.grpc.ScoringRequest;
import com.frauddetection.grpc.ScoringResponse;
import com.frauddetection.grpc.TransactionFeatures;
import com.fraudshield.common.dto.TransactionEvent;
import com.fraudshield.common.enums.DecisionType;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GrpcScoringClient {

  private static final Logger log = LoggerFactory.getLogger(
    GrpcScoringClient.class
  );
  private final FraudScoringServiceGrpc.FraudScoringServiceBlockingStub blockingStub;

  public GrpcScoringClient(
    FraudScoringServiceGrpc.FraudScoringServiceBlockingStub blockingStub
  ) {
    this.blockingStub = blockingStub;
  }

  public ScoreResult score(
    TransactionEvent event,
    FeatureEngineeringService.FeatureSnapshot features
  ) {
    long started = System.nanoTime();
    try {
      ScoringResponse response = blockingStub
        .withDeadlineAfter(2, TimeUnit.SECONDS)
        .scoreTransaction(
          ScoringRequest.newBuilder()
            .setTransactionId(event.transactionId().toString())
            .setUserId(nullSafe(event.userId()))
            .setAmount(event.amount().doubleValue())
            .setMerchantId(nullSafe(event.merchantId()))
            .setCategory(nullSafe(event.category()))
            .setLocation(nullSafe(event.location()))
            .setDeviceId(nullSafe(event.deviceId()))
            .setIpAddress(nullSafe(event.ipAddress()))
            .setChannel(nullSafe(event.channel()))
            .setTimestamp(event.timestamp().toEpochMilli())
            .setFeatures(
              TransactionFeatures.newBuilder()
                .setTxnCount1M(features.txnCount1m())
                .setTxnCount10M(features.txnCount10m())
                .setTxnCount1H(features.txnCount1h())
                .setAmountSum1H(features.amountSum1h())
                .setAvgAmount(features.avgAmount())
                .setAmountDeviation(features.amountDeviation())
                .setIsKnownDevice(features.knownDevice())
                .setIsKnownLocation(features.knownLocation())
                .setUniqueMerchants1H(features.uniqueMerchants1h())
                .setMaxAmount24H(features.maxAmount24h())
                .build()
            )
            .build()
        );
      return new ScoreResult(
        response.getRiskScore(),
        parseDecision(response.getDecision()),
        response.getVelocityScore(),
        response.getGeoAnomalyScore(),
        response.getAmountAnomalyScore(),
        response.getMlModelScore(),
        Math.max(1, (int) Math.ceil(response.getProcessingTimeUs() / 1000.0)),
        response.getTriggeredRulesList()
      );
    } catch (StatusRuntimeException exception) {
      log.warn(
        "Scoring service unavailable ({}); using fallback for transaction {}",
        exception.getStatus().getCode(),
        event.transactionId()
      );
      return heuristicFallback(event, features, started);
    }
  }

  private ScoreResult heuristicFallback(
    TransactionEvent event,
    FeatureEngineeringService.FeatureSnapshot features,
    long started
  ) {
    List<String> triggeredRules = new ArrayList<>();
    triggeredRules.add("SCORING_FALLBACK");
    double velocityScore = Math.min(1.0, features.txnCount1m() / 6.0);
    double geoScore =
      !features.knownDevice() && !features.knownLocation()
        ? 0.85
        : !features.knownLocation()
          ? 0.55
          : 0.10;
    double amountScore = Math.min(
      1.0,
      Math.max(
        features.amountDeviation() / 5.0,
        event.amount().doubleValue() / 100000.0
      )
    );
    if (features.txnCount1m() > 5) {
      triggeredRules.add("VELOCITY_BURST");
    }
    if (event.amount().doubleValue() >= 100000.0) {
      triggeredRules.add("HIGH_AMOUNT");
    }
    if (features.amountDeviation() >= 5.0) {
      triggeredRules.add("AMOUNT_SPIKE");
    }
    if (!features.knownDevice() && !features.knownLocation()) {
      triggeredRules.add("NEW_DEVICE_LOCATION");
    }
    if (features.uniqueMerchants1h() >= 8) {
      triggeredRules.add("MERCHANT_HOPPING");
    }
    if (features.amountSum1h() >= 200000.0) {
      triggeredRules.add("CUMULATIVE_HIGH");
    }
    double riskScore = Math.min(
      1.0,
      velocityScore * 0.35 + geoScore * 0.25 + amountScore * 0.40
    );
    if (
      features.txnCount1m() > 5 ||
      (event.amount().doubleValue() >= 100000.0 &&
        !features.knownDevice() &&
        !features.knownLocation())
    ) {
      riskScore = Math.max(0.85, riskScore);
    }
    DecisionType decision =
      riskScore >= 0.85
        ? DecisionType.BLOCK
        : riskScore >= 0.55
          ? DecisionType.REVIEW
          : DecisionType.ALLOW;
    return new ScoreResult(
      riskScore,
      decision,
      velocityScore,
      geoScore,
      amountScore,
      0.0,
      Math.max(1, (int) Math.ceil((System.nanoTime() - started) / 1_000_000.0)),
      triggeredRules
    );
  }

  private DecisionType parseDecision(String value) {
    try {
      return DecisionType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return DecisionType.REVIEW;
    }
  }

  private String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
