package com.mvbr.retailstore.payment.infrastructure.adapter.in.web;

import com.mvbr.retailstore.payment.application.service.StripeWebhookService;
import com.mvbr.retailstore.payment.config.StripeProperties;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeWebhookController.class)
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeWebhookService webhookService;

    @MockBean
    private StripeProperties stripeProperties;

    @Test
    void rejectsInvalidSignature() throws Exception {
        String payload = "{\"id\":\"evt_invalid\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_1\",\"object\":\"payment_intent\"}}}";
        when(stripeProperties.getWebhookSecret()).thenReturn("whsec_test");

        mockMvc.perform(post("/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", "t=123,v1=invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsValidSignature() throws Exception {
        String secret = "whsec_test";
        String payload = "{\"id\":\"evt_valid\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_2\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}}}";
        when(stripeProperties.getWebhookSecret()).thenReturn(secret);

        String signatureHeader = signedHeader(payload, secret);

        mockMvc.perform(post("/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", signatureHeader))
                .andExpect(status().isOk());

        verify(webhookService).process(ArgumentMatchers.any());
    }

    private String signedHeader(String payload, String secret) throws Exception {
        long timestamp = Webhook.Util.getTimeNow();
        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(secret, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }
}
