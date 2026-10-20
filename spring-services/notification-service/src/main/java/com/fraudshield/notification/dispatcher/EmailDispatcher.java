package com.fraudshield.notification.dispatcher;

import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.common.enums.AlertSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailDispatcher implements NotificationDispatcher {

  private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
  private final JavaMailSender mailSender;
  private final String recipient;

  public EmailDispatcher(
    ObjectProvider<JavaMailSender> mailSender,
    @Value("${fraud.notifications.email.to:}") String recipient
  ) {
    this.mailSender = mailSender.getIfAvailable();
    this.recipient = recipient;
  }

  @Override
  public void dispatch(FraudAlertEvent event) {
    log.info("[Email] Sending fraud alert for transaction {}", event.transactionId());
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(recipient);
      message.setSubject("CRITICAL FRAUD ALERT: " + event.transactionId());
      message.setText(
        String.format(
          "Fraudulent activity detected!\nAmount: %s\nLocation: %s\nRisk Score: %.2f\nRules: %s",
          event.amount(),
          event.location(),
          event.riskScore(),
          String.join(", ", event.triggeredRules())
        )
      );
      mailSender.send(message);
    } catch (Exception e) {
      log.error(
        "[Email] Delivery failed for transaction={} ({})",
        event.transactionId(),
        e.getClass().getSimpleName()
      );
    }
  }

  @Override
  public boolean supports(FraudAlertEvent event) {
    return (
      mailSender != null &&
      !recipient.isBlank() &&
      !Boolean.TRUE.equals(event.isSandbox()) &&
      (event.severity() == AlertSeverity.CRITICAL || event.severity() == AlertSeverity.HIGH)
    );
  }
}
