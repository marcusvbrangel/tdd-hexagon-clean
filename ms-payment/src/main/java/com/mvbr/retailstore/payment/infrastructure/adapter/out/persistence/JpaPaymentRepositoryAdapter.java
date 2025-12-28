package com.mvbr.retailstore.payment.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.payment.application.port.out.PaymentRepository;
import com.mvbr.retailstore.payment.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.payment.domain.model.CustomerId;
import com.mvbr.retailstore.payment.domain.model.OrderId;
import com.mvbr.retailstore.payment.domain.model.Payment;
import com.mvbr.retailstore.payment.domain.model.PaymentId;
import com.mvbr.retailstore.payment.domain.model.PaymentStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Adapter JPA que implementa as portas de persistencia do payment.
 */
@Component
public class JpaPaymentRepositoryAdapter implements PaymentRepository, ProcessedMessageRepository {

    private final JpaPaymentSpringDataRepository paymentRepository;
    private final JpaProcessedMessageSpringDataRepository processedRepository;

    public JpaPaymentRepositoryAdapter(JpaPaymentSpringDataRepository paymentRepository,
                                       JpaProcessedMessageSpringDataRepository processedRepository) {
        this.paymentRepository = paymentRepository;
        this.processedRepository = processedRepository;
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            return Optional.empty();
        }
        return paymentRepository.findByProviderPaymentId(providerPaymentId).map(this::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        JpaPaymentEntity entity = paymentRepository.findById(payment.getPaymentId().value())
                .orElseGet(() -> new JpaPaymentEntity(
                        payment.getPaymentId().value(),
                        payment.getOrderId().value(),
                        payment.getStatus().name(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getCreatedAt(),
                        payment.getUpdatedAt()
                ));

        entity.setCustomerId(payment.getCustomerId() != null ? payment.getCustomerId().value() : null);
        entity.setProviderPaymentId(payment.getProviderPaymentId());
        entity.setStatus(payment.getStatus().name());
        entity.setAmount(payment.getAmount());
        entity.setCurrency(payment.getCurrency());
        entity.setPaymentMethod(payment.getPaymentMethod());
        entity.setReason(payment.getReason());
        entity.setCreatedAt(payment.getCreatedAt());
        entity.setUpdatedAt(payment.getUpdatedAt());
        entity.setLastCommandId(payment.getLastCommandId());
        entity.setCorrelationId(payment.getCorrelationId());

        paymentRepository.save(entity);
        return payment;
    }

    @Override
    public boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt) {
        try {
            processedRepository.save(new JpaProcessedMessageEntity(messageId, messageType, aggregateId, processedAt));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private Payment toDomain(JpaPaymentEntity entity) {
        return new Payment(
                new PaymentId(entity.getPaymentId()),
                entity.getProviderPaymentId(),
                new OrderId(entity.getOrderId()),
                entity.getCustomerId() != null ? new CustomerId(entity.getCustomerId()) : null,
                entity.getAmount(),
                entity.getCurrency(),
                entity.getPaymentMethod(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastCommandId(),
                entity.getCorrelationId()
        );
    }
}
