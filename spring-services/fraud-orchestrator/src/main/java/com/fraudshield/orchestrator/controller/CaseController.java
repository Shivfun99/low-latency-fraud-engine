package com.fraudshield.orchestrator.controller;
import com.fraudshield.common.dto.AnalystActionRequest;
import com.fraudshield.common.dto.CaseSummaryResponse;
import com.fraudshield.orchestrator.service.CaseActionService;
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
public class CaseController {
    private final CaseActionService caseActionService;
    public CaseController(CaseActionService caseActionService) {
        this.caseActionService = caseActionService;
    }
    @GetMapping
    public List<CaseSummaryResponse> recentCases() {
        return caseActionService.recentCases();
    }
    @PostMapping("/{transactionId}/action")
    public CaseSummaryResponse applyAction(@PathVariable("transactionId") UUID transactionId,
                                           @RequestBody AnalystActionRequest request) {
        return caseActionService.applyAction(transactionId, request);
    }
}
