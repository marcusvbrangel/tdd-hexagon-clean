# Retail Store Microservices

---

## Eventing Guidelines (Official)

### 1) Source of Truth (SoT)
- ms-customer: customer + email + addresses
- ms-catalog: products + base price (POC)
- ms-inventory: stock + reservations
- ms-order: order lifecycle + snapshots (address/items/price at place time)
- ms-payment: payment transactions
- ms-shipping: shipping + delivery facts
- ms-invoice: invoices
- ms-notification: email delivery + idempotency audit

### 2) Kafka Message Standard
**Key**
- All `order.*` events MUST use `orderId` as the message key.

**Headers (required)**
- eventId (UUID)              -> idempotency/deduplication
- eventType (string)          -> e.g. OrderPlaced, OrderConfirmed
- schemaVersion ("1")         -> backward compatibility
- producer (string)           -> e.g. ms-order
- occurredAt (ISO-8601 UTC)
- correlationId (UUID/string) -> end-to-end tracing across services
- causationId (UUID/string)   -> the event that caused this one

**Headers (recommended)**
- traceparent (W3C)           -> OpenTelemetry propagation
- contentType: application/json

### 3) Integration Payload
- Integration event payloads SHOULD be "flat" (primitives / arrays).
- Value Objects are kept inside the domain model; adapters map domain -> integration DTO.

### 4) Idempotency (non-negotiable)
Any consumer that performs side-effects MUST be idempotent by `eventId`.
Recommended strategy:
- table `processed_events(event_id, consumer_name, processed_at)`
- ignore if already processed.

For emails:
- table `sent_notifications(event_id, notification_type, recipient, sent_at, status)`
- ignore duplicates.

### 5) Order Snapshots (critical for correctness)
At `place` time, ms-order MUST persist snapshots:
- shippingAddressSnapshot (the address used for this order)
- orderItemsSnapshot (sku, name, qty, unitPrice)
- totalsSnapshot

Reason: catalog/customer data can change after purchase; order must remain auditable.

### 6) Order Completed Ownership (final decision)
- ms-shipping is the source of truth for the real-world delivery fact: `shipping.delivered`
- ms-order derives the aggregate final state and publishes: `order.completed`
