package com.fraudshield.notification.service;
import com.fraudshield.common.dto.FraudAlertEvent;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
@Service
public class AlertInboxService {
    private final Deque<FraudAlertEvent> recentAlerts = new ArrayDeque<>();
    public synchronized void add(FraudAlertEvent event) {
        recentAlerts.addFirst(event);
        while (recentAlerts.size() > 50) {
            recentAlerts.removeLast();
        }
    }
    public synchronized List<FraudAlertEvent> list() {
        return new ArrayList<>(recentAlerts);
    }
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "notification-service",
                "cached_alerts", recentAlerts.size(),
                "timestamp", Instant.now().toString()
        );
    }
}
