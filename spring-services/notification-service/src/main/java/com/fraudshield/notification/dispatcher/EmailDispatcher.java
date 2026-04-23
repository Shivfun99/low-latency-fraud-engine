package com.fraudshield.notification.dispatcher;
import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.common.enums.AlertSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service
public class EmailDispatcher implements NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    private final JavaMailSender mailSender;
    public EmailDispatcher(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    @Override
    public void dispatch(FraudAlertEvent event) {
        log.info("[Email] Sending fraud alert for transaction {}", event.transactionId());
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("security-team@fraudshield.com");
            message.setSubject("CRITICAL FRAUD ALERT: " + event.transactionId());
            message.setText(String.format(
                "Fraudulent activity detected!\nAmount: %s\nLocation: %s\nRisk Score: %.2f\nRules: %s",
                event.amount(), event.location(), event.riskScore(), String.join(", ", event.triggeredRules())
            ));
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[Email] Failed to send alert: {}", e.getMessage());
        }
    }
    @Override
    public boolean supports(FraudAlertEvent event) {
        return event.severity() == AlertSeverity.CRITICAL || event.severity() == AlertSeverity.HIGH;
    }
}
