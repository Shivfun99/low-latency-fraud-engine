package com.fraudshield.notification.service;

import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.notification.dispatcher.NotificationDispatcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationManager {

  private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);
  private final List<NotificationDispatcher> dispatchers;
  private final AlertInboxService alertInboxService;

  public NotificationManager(
    List<NotificationDispatcher> dispatchers,
    AlertInboxService alertInboxService
  ) {
    this.dispatchers = dispatchers;
    this.alertInboxService = alertInboxService;
  }

  public void process(FraudAlertEvent event) {
    alertInboxService.add(event);
    dispatchers
      .stream()
      .filter(d -> d.supports(event))
      .forEach(d -> {
        try {
          d.dispatch(event);
        } catch (Exception e) {
          log.error(
            "Notification dispatcher {} failed for transaction={} ({})",
            d.getClass().getSimpleName(),
            event.transactionId(),
            e.getClass().getSimpleName()
          );
        }
      });
  }
}
