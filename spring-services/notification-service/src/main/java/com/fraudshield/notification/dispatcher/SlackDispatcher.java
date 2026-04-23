package com.fraudshield.notification.dispatcher;
import com.fraudshield.common.dto.FraudAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
@Service
public class SlackDispatcher implements NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SlackDispatcher.class);
    private final RestTemplate restTemplate;
    private final String webhookUrl;
    public SlackDispatcher(@Value("${fraud.notifications.slack.webhook:https:
        this.restTemplate = new RestTemplate();
        this.webhookUrl = webhookUrl;
    }
    @Override
    public void dispatch(FraudAlertEvent event) {
        log.info("[Slack] Posting alert to channel for transaction {}", event.transactionId());
        try {
            String text = String.format("🚨 *Fraud Alert*: Transaction %s\n*User*: %s\n*Amount*: ₹%s\n*Risk*: %.2f",
                    event.transactionId(), event.userId(), event.amount(), event.riskScore());
            restTemplate.postForEntity(webhookUrl, Map.of("text", text), String.class);
        } catch (Exception e) {
            log.warn("[Slack] Failed to post alert (maybe mock URL): {}", e.getMessage());
        }
    }
    @Override
    public boolean supports(FraudAlertEvent event) {
        return true; 
    }
}
