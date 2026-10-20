package com.fraudshield.notification.dispatcher;

import com.fraudshield.common.dto.FraudAlertEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SlackDispatcher implements NotificationDispatcher {

  private static final Logger log = LoggerFactory.getLogger(SlackDispatcher.class);
  private final RestTemplate restTemplate;
  private final String webhookUrl;

  public SlackDispatcher(@Value("${fraud.notifications.slack.webhook:}") String webhookUrl) {
    this.restTemplate = new RestTemplate();
    this.webhookUrl = webhookUrl;
  }

  @Override
  public void dispatch(FraudAlertEvent event) {
    log.info("[Slack] Posting alert to channel for transaction {}", event.transactionId());
    try {
      String text = String.format(
        "🚨 *Fraud Alert*: Transaction %s\n*User*: %s\n*Amount*: ₹%s\n*Risk*: %.2f",
        event.transactionId(),
        event.userId(),
        event.amount(),
        event.riskScore()
      );
      restTemplate.postForEntity(webhookUrl, Map.of("text", text), String.class);
    } catch (Exception e) {
      log.warn(
        "[Slack] Delivery failed for transaction={} ({})",
        event.transactionId(),
        e.getClass().getSimpleName()
      );
    }
  }

  @Override
  public boolean supports(FraudAlertEvent event) {
    return !webhookUrl.isBlank() && !Boolean.TRUE.equals(event.isSandbox());
  }
}
