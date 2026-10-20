package com.fraudshield.orchestrator.repository;

import com.fraudshield.common.entity.FraudDecisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDecisionRepository extends JpaRepository<FraudDecisionEntity, UUID> {
  Optional<FraudDecisionEntity> findTopByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
}
