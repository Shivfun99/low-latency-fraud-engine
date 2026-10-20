package com.fraudshield.casemanagement.repository;

import com.fraudshield.common.entity.FraudAlertEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlertEntity, UUID> {
  Optional<FraudAlertEntity> findTopByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
  List<FraudAlertEntity> findTop50ByOrderByCreatedAtDesc();
}
