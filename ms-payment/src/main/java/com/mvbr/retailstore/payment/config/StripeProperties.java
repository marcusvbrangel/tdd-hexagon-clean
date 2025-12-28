package com.mvbr.retailstore.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuracao do Stripe.
 */
@Component
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    private String apiKey;
    private String webhookSecret;
    private String connectAccount;
    private String defaultPaymentMethodId = "pm_card_visa";
    private String captureMethod = "manual";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getConnectAccount() {
        return connectAccount;
    }

    public void setConnectAccount(String connectAccount) {
        this.connectAccount = connectAccount;
    }

    public String getDefaultPaymentMethodId() {
        return defaultPaymentMethodId;
    }

    public void setDefaultPaymentMethodId(String defaultPaymentMethodId) {
        this.defaultPaymentMethodId = defaultPaymentMethodId;
    }

    public String getCaptureMethod() {
        return captureMethod;
    }

    public void setCaptureMethod(String captureMethod) {
        this.captureMethod = captureMethod;
    }
}
