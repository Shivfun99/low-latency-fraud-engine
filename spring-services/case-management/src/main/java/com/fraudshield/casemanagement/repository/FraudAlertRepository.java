package com.fraudshield.casemanagement.repository;
import com.fraudshield.common.entity.FraudAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface FraudAlertRepository extends JpaRepository<FraudAlertEntity, UUID> {
    Optional<FraudAlertEntity> findTopByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
    List<FraudAlertEntity> findTop50ByOrderByCreatedAtDesc();
}
