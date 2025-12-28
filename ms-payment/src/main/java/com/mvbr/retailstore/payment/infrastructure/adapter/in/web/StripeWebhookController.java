package com.mvbr.retailstore.payment.infrastructure.adapter.in.web;

import com.mvbr.retailstore.payment.application.service.StripeWebhookService;
import com.mvbr.retailstore.payment.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

/**
 * Endpoint para receber webhooks do Stripe.
 */
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private static final Logger log = Logger.getLogger(StripeWebhookController.class.getName());

    private final StripeProperties properties;
    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeProperties properties, StripeWebhookService webhookService) {
        this.properties = properties;
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload,
                                                 @RequestHeader("Stripe-Signature") String signature) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("stripe.webhookSecret not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, properties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warning("Stripe webhook signature verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (RuntimeException e) {
            log.warning("Stripe webhook payload parse failed: " + e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        try {
            webhookService.process(event);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warning("Stripe webhook processing failed eventId=" + event.getId() + " error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
