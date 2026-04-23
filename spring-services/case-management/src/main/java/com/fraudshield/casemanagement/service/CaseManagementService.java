package com.fraudshield.casemanagement.service;
import com.fraudshield.casemanagement.repository.AnalystActionRepository;
import com.fraudshield.casemanagement.repository.FraudAlertRepository;
import com.fraudshield.casemanagement.repository.FraudDecisionRepository;
import com.fraudshield.casemanagement.repository.TransactionRepository;
import com.fraudshield.common.dto.AnalystActionRequest;
import com.fraudshield.common.dto.CaseSummaryResponse;
import com.fraudshield.common.entity.AnalystActionEntity;
import com.fraudshield.common.entity.FraudAlertEntity;
import com.fraudshield.common.entity.FraudDecisionEntity;
import com.fraudshield.common.entity.TransactionEntity;
import com.fraudshield.common.enums.AlertStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
@Service
public class CaseManagementService {
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudDecisionRepository fraudDecisionRepository;
    private final TransactionRepository transactionRepository;
    private final AnalystActionRepository analystActionRepository;
    public CaseManagementService(FraudAlertRepository fraudAlertRepository,
                                 FraudDecisionRepository fraudDecisionRepository,
                                 TransactionRepository transactionRepository,
                                 AnalystActionRepository analystActionRepository) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.fraudDecisionRepository = fraudDecisionRepository;
        this.transactionRepository = transactionRepository;
        this.analystActionRepository = analystActionRepository;
    }
    public List<CaseSummaryResponse> listCases() {
        return fraudAlertRepository.findTop50ByOrderByCreatedAtDesc().stream().map(this::toSummary).toList();
    }
    public CaseSummaryResponse getCase(UUID transactionId) {
        FraudAlertEntity alert = fraudAlertRepository.findTopByTransactionIdOrderByCreatedAtDesc(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        return toSummary(alert);
    }
    @Transactional
    public CaseSummaryResponse applyAction(UUID transactionId, AnalystActionRequest request) {
        FraudAlertEntity alert = fraudAlertRepository.findTopByTransactionIdOrderByCreatedAtDesc(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        String action = request.action() == null ? "REVIEW" : request.action().trim().toUpperCase(Locale.ROOT);
        alert.setAnalystId(request.analystId() == null || request.analystId().isBlank() ? "case-management" : request.analystId());
        alert.setStatus(resolveStatus(action));
        if (alert.getStatus() == AlertStatus.RESOLVED || alert.getStatus() == AlertStatus.DISMISSED) {
            alert.setResolvedAt(Instant.now());
        }
        fraudAlertRepository.save(alert);
        AnalystActionEntity audit = new AnalystActionEntity();
        audit.setAlertId(alert.getId());
        audit.setAnalystId(alert.getAnalystId());
        audit.setAction(action);
        audit.setNotes(request.notes() == null || request.notes().isBlank()
                ? "Action " + action + " applied through case-management"
                : request.notes());
        analystActionRepository.save(audit);
        return toSummary(alert);
    }
    private CaseSummaryResponse toSummary(FraudAlertEntity alert) {
        TransactionEntity transaction = transactionRepository.findById(alert.getTransactionId()).orElse(null);
        FraudDecisionEntity decision = alert.getDecisionId() == null ? null : fraudDecisionRepository.findById(alert.getDecisionId()).orElse(null);
        return new CaseSummaryResponse(
                alert.getId(),
                alert.getDecisionId(),
                alert.getTransactionId(),
                transaction == null ? "unknown" : transaction.getUserId(),
                transaction == null ? null : transaction.getAmount(),
                transaction == null ? null : transaction.getLocation(),
                decision == null || decision.getRiskScore() == null ? 0.0 : decision.getRiskScore().doubleValue(),
                decision == null ? null : decision.getDecision(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getDescription(),
                alert.getCreatedAt(),
                alert.getResolvedAt()
        );
    }
    private AlertStatus resolveStatus(String action) {
        return switch (action) {
            case "APPROVE", "DISMISS" -> AlertStatus.DISMISSED;
            case "BLOCK", "RESOLVE" -> AlertStatus.RESOLVED;
            default -> AlertStatus.INVESTIGATING;
        };
    }
}
