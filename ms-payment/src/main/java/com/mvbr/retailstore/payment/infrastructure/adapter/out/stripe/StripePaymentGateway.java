package com.mvbr.retailstore.payment.infrastructure.adapter.out.stripe;

import com.mvbr.retailstore.payment.application.port.out.PaymentAuthorizationRequest;
import com.mvbr.retailstore.payment.application.port.out.PaymentAuthorizationResult;
import com.mvbr.retailstore.payment.application.port.out.PaymentCaptureRequest;
import com.mvbr.retailstore.payment.application.port.out.PaymentCaptureResult;
import com.mvbr.retailstore.payment.application.port.out.PaymentGateway;
import com.mvbr.retailstore.payment.config.StripeProperties;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Currency;

/**
 * Integracao com Stripe para autorizacao e captura de pagamentos.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final String AUTHORIZE_PREFIX = "payment.authorize:";
    private static final String CAPTURE_PREFIX = "payment.capture:";

    private final StripeProperties properties;

    public StripePaymentGateway(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentAuthorizationResult authorize(PaymentAuthorizationRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Stripe apiKey not configured");
        }

        String currency = normalizeCurrency(request.currency());
        long amount = toMinorUnits(request.amount(), currency);
        String paymentMethod = resolvePaymentMethod(request.paymentMethod());

        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency.toLowerCase())
                .setConfirm(true)
                .setPaymentMethod(paymentMethod);

        builder.setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build()
        );

        PaymentIntentCreateParams.CaptureMethod captureMethod = resolveCaptureMethod();
        if (captureMethod != null) {
            builder.setCaptureMethod(captureMethod);
        }

        putMetadata(builder, "orderId", request.orderId());
        putMetadata(builder, "commandId", request.commandId());
        putMetadata(builder, "correlationId", request.correlationId());
        putMetadata(builder, "sagaId", request.sagaId());

        String idempotencyKey = buildAuthorizeIdempotencyKey(request, currency, amount, paymentMethod, captureMethod);
        RequestOptions.RequestOptionsBuilder options = requestOptions(apiKey, idempotencyKey);

        try {
            PaymentIntent intent = PaymentIntent.create(builder.build(), options.build());
            return mapAuthorization(intent);
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe authorization failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentCaptureResult capture(PaymentCaptureRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Stripe apiKey not configured");
        }

        String providerPaymentId = resolveProviderPaymentId(request);
        String idempotencyKey = buildCaptureIdempotencyKey(request, providerPaymentId);
        RequestOptions.RequestOptionsBuilder options = requestOptions(apiKey, idempotencyKey);

        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerPaymentId, options.build());
            String status = intent.getStatus();

            if ("succeeded".equalsIgnoreCase(status)) {
                return new PaymentCaptureResult(true, intent.getId(), status, null);
            }
            if (!"requires_capture".equalsIgnoreCase(status)) {
                return PaymentCaptureResult.failed(intent.getId(), status, "NOT_CAPTURABLE");
            }

            PaymentIntent captured = intent.capture(options.build());
            String capturedStatus = captured.getStatus();
            if ("succeeded".equalsIgnoreCase(capturedStatus)) {
                return new PaymentCaptureResult(true, captured.getId(), capturedStatus, null);
            }
            return PaymentCaptureResult.failed(captured.getId(), capturedStatus, extractDeclineReason(captured));
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe capture failed: " + e.getMessage(), e);
        }
    }

    PaymentAuthorizationResult mapAuthorization(PaymentIntent intent) {
        String status = intent.getStatus();
        if ("requires_capture".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status)) {
            return new PaymentAuthorizationResult(true, intent.getId(), status, null);
        }
        if ("requires_action".equalsIgnoreCase(status)) {
            return new PaymentAuthorizationResult(false, intent.getId(), status, "REQUIRES_ACTION");
        }
        if ("requires_payment_method".equalsIgnoreCase(status)) {
            return new PaymentAuthorizationResult(false, intent.getId(), status, "REQUIRES_PAYMENT_METHOD");
        }
        if ("canceled".equalsIgnoreCase(status)) {
            return new PaymentAuthorizationResult(false, intent.getId(), status, "CANCELED");
        }

        String declineReason = extractDeclineReason(intent);
        if (declineReason == null || declineReason.isBlank()) {
            declineReason = status == null ? "DECLINED" : "STRIPE_STATUS_" + status.toUpperCase();
        }

        return new PaymentAuthorizationResult(false, intent.getId(), status, declineReason);
    }

    private String resolveProviderPaymentId(PaymentCaptureRequest request) {
        if (request.providerPaymentId() != null && !request.providerPaymentId().isBlank()) {
            return request.providerPaymentId();
        }
        if (request.paymentId() != null && !request.paymentId().isBlank()) {
            return request.paymentId();
        }
        throw new IllegalArgumentException("providerPaymentId is required for capture");
    }

    private String extractDeclineReason(PaymentIntent intent) {
        if (intent.getLastPaymentError() == null) {
            return null;
        }
        String decline = intent.getLastPaymentError().getDeclineCode();
        if (decline != null && !decline.isBlank()) {
            return decline.toUpperCase();
        }
        String code = intent.getLastPaymentError().getCode();
        if (code != null && !code.isBlank()) {
            return code.toUpperCase();
        }
        return null;
    }

    private String resolvePaymentMethod(String paymentMethod) {
        String defaultMethod = properties.getDefaultPaymentMethodId();
        if (paymentMethod == null || paymentMethod.isBlank()) {
            if (defaultMethod == null || defaultMethod.isBlank()) {
                throw new IllegalArgumentException("paymentMethod is required for Stripe authorization");
            }
            return defaultMethod;
        }

        String trimmed = paymentMethod.trim();
        if (isAliasPaymentMethod(trimmed)) {
            if (defaultMethod == null || defaultMethod.isBlank()) {
                throw new IllegalArgumentException("defaultPaymentMethodId is required for alias paymentMethod");
            }
            return defaultMethod;
        }
        return trimmed;
    }

    private boolean isAliasPaymentMethod(String method) {
        String normalized = method.toLowerCase();
        return normalized.equals("card")
                || normalized.equals("test_token")
                || normalized.equals("test")
                || normalized.equals("token");
    }

    private long toMinorUnits(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required for Stripe authorization");
        }
        int fraction = 2;
        try {
            Currency cur = Currency.getInstance(currency.toUpperCase());
            fraction = cur.getDefaultFractionDigits();
            if (fraction < 0) {
                fraction = 2;
            }
        } catch (IllegalArgumentException ignored) {
            fraction = 2;
        }

        BigDecimal scaled = amount.setScale(fraction, RoundingMode.HALF_UP);
        return scaled.movePointRight(fraction).longValueExact();
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "BRL";
        }
        return currency.trim();
    }

    private PaymentIntentCreateParams.CaptureMethod resolveCaptureMethod() {
        String captureMethod = properties.getCaptureMethod();
        if (captureMethod == null || captureMethod.isBlank()) {
            return null;
        }
        if ("manual".equalsIgnoreCase(captureMethod)) {
            return PaymentIntentCreateParams.CaptureMethod.MANUAL;
        }
        if ("automatic".equalsIgnoreCase(captureMethod)) {
            return PaymentIntentCreateParams.CaptureMethod.AUTOMATIC;
        }
        return null;
    }

    private void putMetadata(PaymentIntentCreateParams.Builder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.putMetadata(key, value);
        }
    }

    private RequestOptions.RequestOptionsBuilder requestOptions(String apiKey, String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder options = RequestOptions.builder().setApiKey(apiKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            options.setIdempotencyKey(idempotencyKey);
        }
        if (properties.getConnectAccount() != null && !properties.getConnectAccount().isBlank()) {
            options.setStripeAccount(properties.getConnectAccount());
        }
        return options;
    }

    private String buildAuthorizeIdempotencyKey(PaymentAuthorizationRequest request,
                                                String currency,
                                                long amount,
                                                String paymentMethod,
                                                PaymentIntentCreateParams.CaptureMethod captureMethod) {
        String fingerprint = String.join("|",
                "v1",
                "amount=" + amount,
                "currency=" + currency.toLowerCase(),
                "paymentMethod=" + paymentMethod,
                "captureMethod=" + (captureMethod == null ? "" : captureMethod.name()),
                "automaticPaymentMethods=true",
                "allowRedirects=never",
                "confirm=true",
                "orderId=" + nullSafe(request.orderId()),
                "customerId=" + nullSafe(request.customerId()),
                "correlationId=" + nullSafe(request.correlationId()),
                "sagaId=" + nullSafe(request.sagaId())
        );
        return prefixedIdempotencyKey(AUTHORIZE_PREFIX, request.commandId(), fingerprint);
    }

    private String buildCaptureIdempotencyKey(PaymentCaptureRequest request, String providerPaymentId) {
        String fingerprint = String.join("|",
                "v1",
                "providerPaymentId=" + nullSafe(providerPaymentId),
                "orderId=" + nullSafe(request.orderId()),
                "paymentId=" + nullSafe(request.paymentId()),
                "correlationId=" + nullSafe(request.correlationId()),
                "sagaId=" + nullSafe(request.sagaId())
        );
        return prefixedIdempotencyKey(CAPTURE_PREFIX, request.commandId(), fingerprint);
    }

    private String prefixedIdempotencyKey(String prefix, String commandId, String fingerprint) {
        if (commandId == null || commandId.isBlank()) {
            return null;
        }
        String suffix = "";
        if (fingerprint != null && !fingerprint.isBlank()) {
            suffix = ":" + sha256Hex(fingerprint);
        }
        return prefix + commandId + suffix;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
