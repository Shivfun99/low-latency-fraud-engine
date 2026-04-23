package com.fraudshield.notification.listener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.notification.service.AlertInboxService;
import com.fraudshield.notification.service.NotificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class AlertListener {
    private static final Logger log = LoggerFactory.getLogger(AlertListener.class);
    private final ObjectMapper objectMapper;
    private final NotificationManager notificationManager;
    public AlertListener(ObjectMapper objectMapper, NotificationManager notificationManager) {
        this.objectMapper = objectMapper;
        this.notificationManager = notificationManager;
    }
    @KafkaListener(topics = "${fraud.kafka.topics.alerts}", groupId = "${spring.kafka.consumer.group-id}")
    public void onAlert(String payload) throws JsonProcessingException {
        FraudAlertEvent event = objectMapper.readValue(payload, FraudAlertEvent.class);
        notificationManager.process(event);
        log.warn("Notification processed for transaction={} decision={} severity={}",
                event.transactionId(), event.decision(), event.severity());
    }
}
