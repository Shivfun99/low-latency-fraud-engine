package com.fraudshield.casemanagement.controller;
import com.fraudshield.casemanagement.service.CaseManagementService;
import com.fraudshield.common.dto.AnalystActionRequest;
import com.fraudshield.common.dto.CaseSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/cases")
public class CaseManagementController {
    private final CaseManagementService caseManagementService;
    public CaseManagementController(CaseManagementService caseManagementService) {
        this.caseManagementService = caseManagementService;
    }
    @GetMapping
    public List<CaseSummaryResponse> listCases() {
        return caseManagementService.listCases();
    }
    @GetMapping("/{transactionId}")
    public CaseSummaryResponse getCase(@PathVariable("transactionId") UUID transactionId) {
        return caseManagementService.getCase(transactionId);
    }
    @PostMapping("/{transactionId}/action")
    public CaseSummaryResponse applyAction(@PathVariable("transactionId") UUID transactionId,
                                           @RequestBody AnalystActionRequest request) {
        return caseManagementService.applyAction(transactionId, request);
    }
}
