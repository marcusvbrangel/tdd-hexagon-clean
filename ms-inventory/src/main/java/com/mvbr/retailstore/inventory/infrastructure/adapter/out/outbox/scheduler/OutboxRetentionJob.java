package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.scheduler;

import com.mvbr.retailstore.inventory.config.InventoryOutboxProperties;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxMessageJpaEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

/**
 * Job de limpeza de mensagens publicadas antigas.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionJob {

    private static final Logger log = Logger.getLogger(OutboxRetentionJob.class.getName());

    private final OutboxJpaRepository outboxRepository;
    private final InventoryOutboxProperties props;

    public OutboxRetentionJob(OutboxJpaRepository outboxRepository,
                              InventoryOutboxProperties props) {
        this.outboxRepository = outboxRepository;
        this.props = props;
    }

    /**
     * Remove mensagens publicadas mais antigas que a janela de retencao.
     *
     * cron vem de outbox.retention.cron
     */
    @Scheduled(cron = "${outbox.retention.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupPublished() {
        int retentionDays = props.getRetention().getRetentionDays();
        if (retentionDays <= 0) retentionDays = 7;

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        long deleted = outboxRepository.deleteByStatusAndPublishedAtBefore(
                OutboxMessageJpaEntity.Status.PUBLISHED.name(),
                cutoff
        );

        if (deleted > 0) {
            log.info("OutboxRetentionJob removed " + deleted + " published messages older than " + cutoff);
        }
    }
}
