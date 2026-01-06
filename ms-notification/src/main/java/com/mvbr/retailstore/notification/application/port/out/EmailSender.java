package com.mvbr.retailstore.notification.application.port.out;

public interface EmailSender {
    void send(String to, String subject, String textBody, String htmlBody);
}
