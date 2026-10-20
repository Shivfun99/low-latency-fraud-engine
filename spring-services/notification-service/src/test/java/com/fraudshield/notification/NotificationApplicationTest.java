package com.fraudshield.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fraudshield.common.dto.FraudAlertEvent;
import com.fraudshield.common.enums.AlertSeverity;
import com.fraudshield.notification.dispatcher.NotificationDispatcher;
import com.fraudshield.notification.service.AlertInboxService;
import com.fraudshield.notification.service.NotificationManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class NotificationApplicationTest {

  @Autowired
  List<NotificationDispatcher> dispatchers;

  @Autowired
  NotificationManager manager;

  @Autowired
  AlertInboxService inbox;

  @Test
  void startsWithoutSmtpAndKeepsAlertsWithoutExternalDeliveryByDefault() {
    var event = mock(FraudAlertEvent.class);
    when(event.severity()).thenReturn(AlertSeverity.CRITICAL);
    when(event.isSandbox()).thenReturn(false);
    assertThat(dispatchers).allMatch(dispatcher -> !dispatcher.supports(event));
    manager.process(event);
    assertThat(inbox.list()).contains(event);
  }
}
