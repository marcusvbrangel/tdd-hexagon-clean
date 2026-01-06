package com.mvbr.retailstore.notification.infrastructure.adapter.out.email;

import com.mvbr.retailstore.notification.application.port.out.EmailSender;
import com.mvbr.retailstore.notification.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setFrom(properties.getEmail().getFrom());
            helper.setSubject(subject);

            String safeText = textBody == null ? "" : textBody;
            if (htmlBody != null && !htmlBody.isBlank()) {
                helper.setText(safeText, htmlBody);
            } else {
                helper.setText(safeText);
            }
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to prepare email", ex);
        }
    }
}
