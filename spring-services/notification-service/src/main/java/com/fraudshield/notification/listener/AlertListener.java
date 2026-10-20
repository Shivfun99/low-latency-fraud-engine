package com.fraudshield.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.notification.service.NotificationManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertListener {

  private final ObjectMapper objectMapper;
  private final NotificationManager notificationManager;

  public AlertListener(ObjectMapper objectMapper, NotificationManager notificationManager) {
    this.objectMapper = objectMapper;
    this.notificationManager = notificationManager;
  }

  @KafkaListener(
    topics = "${fraud.kafka.topics.alerts}",
    groupId = "${spring.kafka.consumer.group-id}"
  )
  public void onAlert(String payload) throws JsonProcessingException {
    FraudAlertEvent event = objectMapper.readValue(payload, FraudAlertEvent.class);
    notificationManager.process(event);
  }
}
