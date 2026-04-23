package com.fraudshield.orchestrator.repository;
import com.fraudshield.common.entity.AnalystActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface AnalystActionRepository extends JpaRepository<AnalystActionEntity, UUID> {
    List<AnalystActionEntity> findTop20ByAlertIdOrderByCreatedAtDesc(UUID alertId);
}
