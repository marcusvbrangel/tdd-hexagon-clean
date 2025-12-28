# ms-payment

Microservico de pagamentos com integracao Stripe (backend-only), idempotencia, outbox e webhooks.

## Variaveis de ambiente

- `STRIPE_API_KEY` (obrigatorio)
- `STRIPE_WEBHOOK_SECRET` (obrigatorio para webhooks)
- `STRIPE_CONNECT_ACCOUNT` (opcional, Connect)
- `STRIPE_DEFAULT_PAYMENT_METHOD_ID` (default: `pm_card_visa`)
- `STRIPE_CAPTURE_METHOD` (default: `manual`)

## Fluxos suportados

### Authorize (Kafka)

Topico: `payment.commands.v1`

Header:
- `x-command-type: payment.authorize`

Payload:
```json
{
  "commandId": "cmd-1",
  "occurredAt": "2025-01-01T10:00:05Z",
  "orderId": "order-1",
  "customerId": "cust-1",
  "amount": "22.50",
  "currency": "BRL",
  "paymentMethod": "card"
}
```

Eventos publicados (outbox) em `payment.events.v1`:
- `payment.authorized`
- `payment.declined`
 - `payment.failed` (via webhook)

Observacao: backend-only nao suporta `requires_action` (3DS). Para testes, use `pm_card_visa`.

### Capture (Kafka)

Topico: `payment.commands.v1`

Header:
- `x-command-type: payment.capture`

Payload:
```json
{
  "commandId": "cmd-2",
  "occurredAt": "2025-01-01T10:05:00Z",
  "orderId": "order-1",
  "paymentId": "pi_123"
}
```

Eventos publicados (outbox) em `payment.events.v1`:
- `payment.captured`
- `payment.capture_failed`

## Webhook Stripe

Endpoint:
- `POST /stripe/webhook`

Para testar localmente com Stripe CLI:

```bash
stripe login
stripe listen --forward-to http://localhost:8094/stripe/webhook
stripe trigger payment_intent.succeeded
stripe trigger payment_intent.payment_failed
```

## Observacoes

- Todos os eventos sao publicados via outbox (nao publicamos direto no Kafka).
- Idempotencia:
  - `commandId` para comandos
  - `event.id` para webhooks
  - `idempotency key` nas chamadas Stripe
- Webhook e a fonte final de verdade para o estado do pagamento.
