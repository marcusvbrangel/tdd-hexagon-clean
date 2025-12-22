package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "outbox.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionJob {

    private static final Logger log = Logger.getLogger(OutboxRetentionJob.class.getName());

    private final OutboxJpaRepository outboxRepository;

    public OutboxRetentionJob(OutboxJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "${outbox.retention.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupPublished() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        long deleted = outboxRepository.deleteByStatusAndPublishedAtBefore(
                OutboxMessageJpaEntity.Status.PUBLISHED.name(),
                cutoff
        );
        if (deleted > 0) {
            log.info("OutboxRetentionJob removed " + deleted + " published messages older than " + cutoff);
        }
    }
}
