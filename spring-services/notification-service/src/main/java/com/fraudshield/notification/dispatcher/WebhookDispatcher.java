package com.fraudshield.notification.dispatcher;
import com.fraudshield.common.dto.FraudAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
@Service
public class WebhookDispatcher implements NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private final RestTemplate restTemplate;
    private final String targetUrl;
    public WebhookDispatcher(@Value("${fraud.notifications.webhook.target:http:
        this.restTemplate = new RestTemplate();
        this.targetUrl = targetUrl;
    }
    @Override
    public void dispatch(FraudAlertEvent event) {
        log.info("[Webhook] Notifying external system at {} for transaction {}", targetUrl, event.transactionId());
        try {
            restTemplate.postForEntity(targetUrl, event, Void.class);
        } catch (Exception e) {
            log.warn("[Webhook] Delivery failed to {}: {}", targetUrl, e.getMessage());
        }
    }
    @Override
    public boolean supports(FraudAlertEvent event) {
        return event.isSandbox() == null || !event.isSandbox(); 
    }
}
