package com.fraudshield.orchestrator.repository;
import com.fraudshield.common.entity.FraudDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface FraudDecisionRepository extends JpaRepository<FraudDecisionEntity, UUID> {
    Optional<FraudDecisionEntity> findTopByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
}
