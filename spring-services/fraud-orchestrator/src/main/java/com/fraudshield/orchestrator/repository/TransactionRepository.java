package com.fraudshield.orchestrator.repository;
import com.fraudshield.common.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
}
