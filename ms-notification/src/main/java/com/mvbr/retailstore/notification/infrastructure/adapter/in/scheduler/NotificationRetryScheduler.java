package com.mvbr.retailstore.notification.infrastructure.adapter.in.scheduler;

import com.mvbr.retailstore.notification.application.service.NotificationRetryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetryScheduler {

    private final NotificationRetryService retryService;

    public NotificationRetryScheduler(NotificationRetryService retryService) {
        this.retryService = retryService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.fixedDelayMs:15000}")
    public void tick() {
        retryService.retryFailed();
    }
}
