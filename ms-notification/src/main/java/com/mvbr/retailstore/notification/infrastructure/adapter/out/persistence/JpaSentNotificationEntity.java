package com.mvbr.retailstore.notification.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sent_notifications")
public class JpaSentNotificationEntity {

    @Id
    @Column(name = "command_id", length = 64)
    private String commandId;

    @Column(name = "template", nullable = false, length = 64)
    private String template;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "recipient", length = 256)
    private String recipient;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "body")
    private String body;

    @Column(name = "body_html")
    private String bodyHtml;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error")
    private String error;

    protected JpaSentNotificationEntity() {
    }

    public JpaSentNotificationEntity(String commandId,
                                     String template,
                                     String orderId,
                                     String customerId,
                                     String status,
                                     Instant createdAt) {
        this.commandId = commandId;
        this.template = template;
        this.orderId = orderId;
        this.customerId = customerId;
        this.attempts = 0;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getTemplate() {
        return template;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getError() {
        return error;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public void setError(String error) {
        this.error = error;
    }

    public enum Status {
        PENDING,
        RETRYING,
        SENT,
        FAILED,
        IGNORED
    }
}
