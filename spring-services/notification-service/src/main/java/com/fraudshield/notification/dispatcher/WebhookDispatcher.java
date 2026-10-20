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

  public WebhookDispatcher(@Value("${fraud.notifications.webhook.target:}") String targetUrl) {
    this.restTemplate = new RestTemplate();
    this.targetUrl = targetUrl;
  }

  @Override
  public void dispatch(FraudAlertEvent event) {
    log.info("[Webhook] Sending alert for transaction {}", event.transactionId());
    try {
      restTemplate.postForEntity(targetUrl, event, Void.class);
    } catch (Exception e) {
      log.warn(
        "[Webhook] Delivery failed for transaction={} ({})",
        event.transactionId(),
        e.getClass().getSimpleName()
      );
    }
  }

  @Override
  public boolean supports(FraudAlertEvent event) {
    return !targetUrl.isBlank() && !Boolean.TRUE.equals(event.isSandbox());
  }
}
