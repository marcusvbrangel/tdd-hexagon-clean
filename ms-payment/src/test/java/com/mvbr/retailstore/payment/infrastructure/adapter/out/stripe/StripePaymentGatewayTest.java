package com.mvbr.retailstore.payment.infrastructure.adapter.out.stripe;

import com.mvbr.retailstore.payment.application.port.out.PaymentAuthorizationResult;
import com.mvbr.retailstore.payment.config.StripeProperties;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripePaymentGatewayTest {

    @Test
    void mapAuthorization_requiresCapture_is_authorized() {
        StripePaymentGateway gateway = new StripePaymentGateway(new StripeProperties());
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_test");
        intent.setStatus("requires_capture");

        PaymentAuthorizationResult result = gateway.mapAuthorization(intent);

        assertThat(result.authorized()).isTrue();
        assertThat(result.providerPaymentId()).isEqualTo("pi_test");
    }

    @Test
    void mapAuthorization_requiresAction_is_declined() {
        StripePaymentGateway gateway = new StripePaymentGateway(new StripeProperties());
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_action");
        intent.setStatus("requires_action");

        PaymentAuthorizationResult result = gateway.mapAuthorization(intent);

        assertThat(result.authorized()).isFalse();
        assertThat(result.declineReason()).isEqualTo("REQUIRES_ACTION");
    }
}
