package com.fraudshield.notification.dispatcher;
import com.fraudshield.common.dto.FraudAlertEvent;
public interface NotificationDispatcher {
    void dispatch(FraudAlertEvent event);
    boolean supports(FraudAlertEvent event);
}
