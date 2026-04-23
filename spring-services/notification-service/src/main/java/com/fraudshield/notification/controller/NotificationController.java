package com.fraudshield.notification.controller;
import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.notification.service.AlertInboxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final AlertInboxService alertInboxService;
    public NotificationController(AlertInboxService alertInboxService) {
        this.alertInboxService = alertInboxService;
    }
    @GetMapping("/recent")
    public List<FraudAlertEvent> recentAlerts() {
        return alertInboxService.list();
    }
    @GetMapping("/health")
    public Map<String, Object> health() {
        return alertInboxService.health();
    }
}
