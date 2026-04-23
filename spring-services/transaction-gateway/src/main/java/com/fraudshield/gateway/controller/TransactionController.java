package com.fraudshield.gateway.controller;
import com.fraudshield.common.dto.TransactionRequest;
import com.fraudshield.gateway.service.TransactionPublisherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionPublisherService transactionPublisherService;
    public TransactionController(TransactionPublisherService transactionPublisherService) {
        this.transactionPublisherService = transactionPublisherService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> ingest(@Valid @RequestBody TransactionRequest request) {
        return transactionPublisherService.publish(request);
    }
}
