package com.mvbr.retailstore.inventory.application.service;

import com.mvbr.retailstore.inventory.application.command.CommitInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryItemCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.application.port.out.InventoryItemRepository;
import com.mvbr.retailstore.inventory.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.inventory.application.port.out.ReservationRepository;
import com.mvbr.retailstore.inventory.config.InventoryProperties;
import com.mvbr.retailstore.inventory.domain.model.InventoryItem;
import com.mvbr.retailstore.inventory.domain.model.OrderId;
import com.mvbr.retailstore.inventory.domain.model.ProductId;
import com.mvbr.retailstore.inventory.domain.model.Quantity;
import com.mvbr.retailstore.inventory.domain.model.Reservation;
import com.mvbr.retailstore.inventory.domain.model.ReservationId;
import com.mvbr.retailstore.inventory.domain.model.ReservationItem;
import com.mvbr.retailstore.inventory.domain.model.ReservationStatus;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.TopicNames;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryCommittedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReleasedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.headers.SagaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Orquestra comandos de inventory com idempotencia, lock de estoque e outbox.
 * Centraliza a regra de negocio para reserva/liberacao.
 */
@Component
public class InventoryCommandService {

    private static final Logger log = Logger.getLogger(InventoryCommandService.class.getName());

    private static final String AGGREGATE_TYPE = "Order";

    private final InventoryItemRepository inventoryRepo;
    private final ReservationRepository reservationRepo;
    private final ProcessedMessageRepository processedRepo;
    private final EventPublisher eventPublisher;
    private final InventoryProperties properties;

    public InventoryCommandService(InventoryItemRepository inventoryRepo,
                                   ReservationRepository reservationRepo,
                                   ProcessedMessageRepository processedRepo,
                                   EventPublisher eventPublisher,
                                   InventoryProperties properties) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
        this.processedRepo = processedRepo;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Reserva estoque de forma atomica (tudo ou nada) e publica inventory.reserved/rejected.
     */
    @Transactional
    public void reserve(ReserveInventoryCommand cmd, SagaContext ctx) {
        final Instant now = Instant.now();

        String orderId = cmd.orderId();
        String commandId = cmd.commandId();

        boolean first = processedRepo.markProcessedIfFirst(commandId, "inventory.reserve", orderId, now);
        if (!first) {
            reservationRepo.findByOrderId(orderId).ifPresentOrElse(
                    existing -> republishReserveOutcome(existing, ctx),
                    () -> log.info("Duplicate inventory.reserve but no reservation found. orderId=" + orderId)
            );
            return;
        }

        Optional<Reservation> existingOpt = reservationRepo.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            republishReserveOutcome(existingOpt.get(), ctx);
            return;
        }

        Reservation reservation = new Reservation(
                new ReservationId(UUID.randomUUID().toString()),
                new OrderId(orderId),
                ReservationStatus.PENDING,
                null,
                now,
                now.plus(properties.getReservation().getTtlSeconds(), ChronoUnit.SECONDS),
                commandId,
                ctx != null ? ctx.correlationId() : null
        );

        reservationRepo.save(reservation);

        List<String> productIds = cmd.items().stream().map(ReserveInventoryItemCommand::productId).toList();
        List<InventoryItem> stocks = inventoryRepo.lockByProductIds(productIds);

        Map<String, InventoryItem> byProduct = new HashMap<>();
        for (InventoryItem s : stocks) {
            byProduct.put(s.getProductId().value(), s);
        }

        for (ReserveInventoryItemCommand it : cmd.items()) {
            InventoryItem stock = byProduct.get(it.productId());
            if (stock == null) {
                reject(reservation, ctx, orderId, "UNKNOWN_PRODUCT:" + it.productId());
                return;
            }
            if (stock.available() < it.quantity()) {
                reject(reservation, ctx, orderId, "INSUFFICIENT_STOCK:" + it.productId());
                return;
            }
        }

        for (ReserveInventoryItemCommand it : cmd.items()) {
            InventoryItem stock = byProduct.get(it.productId());
            stock.reserve(it.quantity(), now); // <-- assinatura "tanque"
            inventoryRepo.save(stock);
            reservation.addItem(new ProductId(it.productId()), new Quantity(it.quantity()));
        }

        reservation.markReserved();
        reservationRepo.save(reservation);

        publishReserved(reservation, ctx);
    }

    /**
     * Libera reserva e publica inventory.released de forma idempotente.
     */
    @Transactional
    public void release(ReleaseInventoryCommand cmd, SagaContext ctx) {
        final Instant now = Instant.now();

        String orderId = cmd.orderId();
        String commandId = cmd.commandId();
        String reason = (cmd.reason() == null || cmd.reason().isBlank()) ? "RELEASED" : cmd.reason();

        boolean first = processedRepo.markProcessedIfFirst(commandId, "inventory.release", orderId, now);
        if (!first) {
            publishReleased(orderId, reason, ctx);
            return;
        }

        Optional<Reservation> reservationOpt = reservationRepo.findByOrderId(orderId);
        if (reservationOpt.isEmpty()) {
            publishReleased(orderId, "NOT_FOUND", ctx);
            return;
        }

        Reservation reservation = reservationOpt.get();

        if (!reservation.isReserved()) {
            publishReleased(orderId, "ALREADY_" + reservation.getStatus().name(), ctx);
            return;
        }

        List<String> productIds = reservation.getItems().stream()
                .map(item -> item.productId().value())
                .toList();
        List<InventoryItem> stocks = inventoryRepo.lockByProductIds(productIds);

        Map<String, InventoryItem> byProduct = new HashMap<>();
        for (InventoryItem s : stocks) {
            byProduct.put(s.getProductId().value(), s);
        }

        for (ReservationItem it : reservation.getItems()) {
            InventoryItem stock = byProduct.get(it.productId().value());
            if (stock == null) {
                throw new IllegalStateException("Inventory item not found for productId=" + it.productId().value());
            }
            stock.release(it.quantity().value(), now); // <-- assinatura "tanque"
            inventoryRepo.save(stock);
        }

        reservation.markReleased(reason);
        reservation.updateLastCommandId(commandId);
        reservationRepo.save(reservation);

        publishReleased(orderId, reason, ctx);
    }

    /**
     * Efetiva a reserva (commit) e publica inventory.committed de forma idempotente.
     */
    @Transactional
    public void commit(CommitInventoryCommand cmd, SagaContext ctx) {
        final Instant now = Instant.now();

        String orderId = cmd.orderId();
        String commandId = cmd.commandId();

        boolean first = processedRepo.markProcessedIfFirst(commandId, "inventory.commit", orderId, now);
        if (!first) {
            reservationRepo.findByOrderId(orderId).ifPresentOrElse(
                    existing -> {
                        if (existing.isCommitted()) {
                            publishCommitted(existing, ctx);
                        } else {
                            log.info("Duplicate inventory.commit ignored. orderId=" + orderId
                                    + " status=" + existing.getStatus());
                        }
                    },
                    () -> log.info("Duplicate inventory.commit but no reservation found. orderId=" + orderId)
            );
            return;
        }

        Optional<Reservation> reservationOpt = reservationRepo.findByOrderId(orderId);
        if (reservationOpt.isEmpty()) {
            log.warning("inventory.commit without reservation. orderId=" + orderId);
            return;
        }

        Reservation reservation = reservationOpt.get();
        if (reservation.isCommitted()) {
            publishCommitted(reservation, ctx);
            return;
        }
        if (!reservation.isReserved()) {
            log.warning("inventory.commit ignored for status=" + reservation.getStatus()
                    + " orderId=" + orderId);
            return;
        }

        List<String> productIds = reservation.getItems().stream()
                .map(item -> item.productId().value())
                .toList();
        List<InventoryItem> stocks = inventoryRepo.lockByProductIds(productIds);

        Map<String, InventoryItem> byProduct = new HashMap<>();
        for (InventoryItem s : stocks) {
            byProduct.put(s.getProductId().value(), s);
        }

        for (ReservationItem it : reservation.getItems()) {
            InventoryItem stock = byProduct.get(it.productId().value());
            if (stock == null) {
                throw new IllegalStateException("Inventory item not found for productId=" + it.productId().value());
            }
            stock.commit(it.quantity().value(), now); // <-- assinatura "tanque"
            inventoryRepo.save(stock);
        }

        reservation.markCommitted();
        reservation.updateLastCommandId(commandId);
        reservationRepo.save(reservation);

        publishCommitted(reservation, ctx);
    }

    private void reject(Reservation reservation, SagaContext ctx, String orderId, String reason) {
        reservation.markRejected(reason);
        reservationRepo.save(reservation);
        publishRejected(orderId, reason, ctx);
    }

    private void republishReserveOutcome(Reservation reservation, SagaContext ctx) {
        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            publishReserved(reservation, ctx);
            return;
        }
        if (reservation.getStatus() == ReservationStatus.REJECTED) {
            publishRejected(reservation.getOrderId().value(), reservation.getReason(), ctx);
            return;
        }
        publishRejected(reservation.getOrderId().value(), "PENDING_STATE", ctx);
    }

    private void publishReserved(Reservation reservation, SagaContext ctx) {
        List<InventoryReservedEventV1.Item> items = reservation.getItems().stream()
                .map(i -> new InventoryReservedEventV1.Item(i.productId().value(), i.quantity().value()))
                .toList();

        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        InventoryReservedEventV1 event = new InventoryReservedEventV1(
                eventId,
                now.toString(),
                reservation.getOrderId().value(),
                reservation.getExpiresAt().toString(),
                items
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                reservation.getOrderId().value(),
                "inventory.reserved",
                event,
                SagaHeaders.forEvent(eventId, "inventory.reserved", now.toString(), AGGREGATE_TYPE,
                        reservation.getOrderId().value(), ctx),
                now
        );
    }

    private void publishRejected(String orderId, String reason, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        InventoryRejectedEventV1 event = new InventoryRejectedEventV1(
                eventId,
                now.toString(),
                orderId,
                reason
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.rejected",
                event,
                SagaHeaders.forEvent(eventId, "inventory.rejected", now.toString(), AGGREGATE_TYPE, orderId, ctx),
                now
        );
    }

    private void publishReleased(String orderId, String reason, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        InventoryReleasedEventV1 event = new InventoryReleasedEventV1(
                eventId,
                now.toString(),
                orderId,
                reason
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.released",
                event,
                SagaHeaders.forEvent(eventId, "inventory.released", now.toString(), AGGREGATE_TYPE, orderId, ctx),
                now
        );
    }

    private void publishCommitted(Reservation reservation, SagaContext ctx) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        InventoryCommittedEventV1 event = new InventoryCommittedEventV1(
                eventId,
                now.toString(),
                reservation.getOrderId().value()
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                reservation.getOrderId().value(),
                "inventory.committed",
                event,
                SagaHeaders.forEvent(eventId, "inventory.committed", now.toString(), AGGREGATE_TYPE,
                        reservation.getOrderId().value(), ctx),
                now
        );
    }
}
