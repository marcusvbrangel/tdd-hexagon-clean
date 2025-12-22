
MEGA CONTEXTO — Projeto Retail Store (até aqui) + Próximo passo (Orquestrador)
1) Visão geral

Estou construindo um sistema de varejo em microserviços (estudo), stack:

Java 21 + Spring Boot 3.4

Postgres (JPA/Hibernate)

Kafka (Confluent cp-kafka + zookeeper) + Redpanda Console (apenas UI)

Comunicação assíncrona via eventos e Outbox Pattern

Sem UI: uso Postman

Tenho liberdade total para quebrar compatibilidade (ninguém consumindo ainda).

2) Estado atual do ms-order (FECHADO)

ms-order está funcionando e publicando eventos no Kafka via Outbox Pattern:

2.1 Topic padrão

Tópico único para eventos do domínio Order:

order.events.v1

Key das mensagens: orderId (String)

Partitions: 3 (dev)

ReplicationFactor: 1 (dev, 1 broker)

2.2 Convenção: eventType não é o nome do tópico

O tópico é sempre order.events.v1.

O “tipo lógico” do evento vai no header:

x-event-type: "order.placed", "order.confirmed", "order.canceled", "order.completed"

2.3 Payload (exemplo real visto no Redpanda Console)
Key: <orderId>
Value (JSON):

{
"eventId": "...",
"occurredAt": "2025-12-22T07:19:36.897150224Z",
"orderId": "...",
"customerId": "CUST-222",
"items": [
{"productId":"PROD-100","quantity":2,"unitPrice":"59.90"},
{"productId":"PROD-200","quantity":1,"unitPrice":"129.90"}
]
}


2.4 Headers padrão (observados no Kafka)

content-type = application/json

x-aggregate-type = Order

x-aggregate-id = <orderId>

x-event-id = <eventId>

x-event-type = order.placed etc

x-occurred-at = ISO UTC

x-producer = ms-order

x-schema-version = v1

x-topic-version = v1

x-correlation-id presente

x-causation-id presente

traceparent opcional (se existir)

2.5 Outbox

Tabela outbox_messages com status PENDING/IN_PROGRESS/PUBLISHED/FAILED, retry/backoff.

OutboxRelay scheduler publica e marca PUBLISHED.

Já foi validado: tick pegou 4 pendentes e virou 0.

2.6 Correções já feitas

Foi corrigido o problema INVALID_REPLICATION_FACTOR e “topic not present”:

O KafkaConfig agora cria apenas order.events.v1 via KafkaAdmin.NewTopics.

Broker é 1 nó → RF=1.

2.7 KafkaConfig final (ms-order)

Cria topic: order.events.v1 (partitions e replicationFactor via kafka.topics.* no YAML).

ProducerFactory usa KafkaProperties.buildProducerProperties(sslBundles) (Spring Boot 3.4).

2.8 SagaHeaders final (ms-order)

Classe SagaHeaders usa MDC (SLF4J MDC) para correlation/causation/traceparent e suporta campos de saga opcionais.

Ajustes aplicados:

SCHEMA_VERSION = "v1"

valida eventId, eventType, occurredAt (não deixa header “podre” sair)

correlationId: MDC se existir, senão UUID novo

causationId: MDC se existir, senão eventId

topicVersion: fallback v1

aggregate headers opcionais

saga headers opcionais via MDC: x-saga-id, x-saga-name, x-saga-step

3) Estrutura do ms-order (macro)

Arquitetura hexagonal/clean:

domain (Order aggregate, OrderStatus etc.)

application (usecases + service)

infrastructure (adapters web, persistence, messaging, outbox)

Publisher atual publica no tópico único order.events.v1

Outbox Relay manda ProducerRecord com headers.

4) Próximo passo desejado (AGORA): criar Orquestrador (Saga Orchestration)

Quero criar um microserviço separado:

Nome sugerido: ms-checkout-orchestrator (ou ms-saga-orchestrator)

Sem frameworks “pesados” (Axon/Temporal/Eventuate): apenas Spring + Kafka + Postgres + Outbox.

Objetivo: coordenar checkout end-to-end por orquestração.

4.1 Princípios desejados

Orquestrador escuta eventos (principalmente order.events.v1 e depois inventory.events.v1, payment.events.v1, etc.)

Mantém estado da saga no banco:

checkout_saga (sagaId, orderId unique, status, step, correlationId, lastEventId, timestamps, version)

Idempotência:

tabela processed_events(event_id unique) ou equivalente

Publica commands para serviços via Kafka (também com outbox no orquestrador):

inventory.commands.v1

payment.commands.v1

shipping.commands.v1

invoice.commands.v1

notification.commands.v1

(opcional) order.commands.v1 para complete/cancel

Todos os tópicos usam key=orderId

Commands e events usam headers x-* (correlation/causation/saga-id/saga-step etc.)

4.2 Happy path recomendado (MVP realista)

Recebe order.placed (order.events.v1)

Orquestrador manda inventory.reserve (inventory.commands.v1)

Recebe inventory.reserved (inventory.events.v1)

Manda payment.authorize (payment.commands.v1)

Recebe payment.authorized (payment.events.v1)

(MVP pode parar aqui e mandar order.complete / order.confirm depois)

Versão completa: shipping → invoice → notification → finalize order.

4.3 Compensação (quando falha)

Se payment falhar após inventory reservar:

manda inventory.release

manda order.cancel

Se inventory falhar:

manda order.cancel

(shipping falhar) → refund/cancel + release.

4.4 Decisão desejada

Preferir comando via Kafka (não HTTP) para consistência.

5) O que eu quero que você faça no novo chat

Quero que você proponha e gere:

Estrutura do projeto ms-checkout-orchestrator (pacotes + classes principais) seguindo estilo do ms-order

Modelo de banco (JPA entities):

CheckoutSagaJpaEntity

ProcessedEventJpaEntity (idempotência)

OutboxMessageJpaEntity + relay (pode reaproveitar o padrão do ms-order)

Contrato de mensagens:

Commands: ex InventoryReserveCommandV1, PaymentAuthorizeCommandV1

Events esperados: inventory.reserved, payment.authorized, etc.

Headers padrão obrigatórios (x-correlation-id, x-causation-id, x-saga-id, x-saga-step, x-event-type, versions)

Primeiro fluxo MVP (somente order.placed → inventory.reserve → inventory.reserved → payment.authorize → payment.authorized → order.complete)

Recomendações profissionais de nomenclatura e boas práticas (idempotência, retries, DLQ no futuro, etc.)

6) Infra local (docker-compose atual)

zookeeper + kafka-1 (1 broker)

kafka listeners:

host: localhost:9092 (PLAINTEXT_HOST)

internal: kafka-1:29092 (para outros containers)

redpanda-console conecta em kafka-1:29092

postgres local localhost:5432

7) Observação sobre MDC

MDC = Mapped Diagnostic Context (SLF4J), usado para propagar correlation/causation/traceparent e alimentar headers/logs.

Agora quero começar o orquestrador.

escreva aqui como vai ser o fluxo do orquestrador... de achordo com o que conversamos no inicio deste chat........     fluxo     e maquina de estado.....    e diga também quais serao os futuros microservicos e o que eles esperam, o que eles recebem e o que ele publicam


Beleza — vou descrever o fluxo do orquestrador (do jeito que combinamos: Kafka + Outbox + topic único por domínio, e x-event-type nos headers) e a máquina de estados. Depois eu listo os microserviços futuros, o que cada um espera/recebe, o que faz, e o que publica.


Fluxo do orquestrador (Checkout Saga Orchestrator)
Tópicos (padrão que vamos seguir)

Eventos (services → mundo):

order.events.v1

inventory.events.v1

payment.events.v1

shipping.events.v1

invoice.events.v1

notification.events.v1

Comandos (orquestrador → services):

inventory.commands.v1

payment.commands.v1

shipping.commands.v1

invoice.commands.v1

notification.commands.v1

(opcional, mas eu recomendo) order.commands.v1

Key sempre = orderId em todos os tópicos.

Headers obrigatórios (padrão “empresa grande”)

Em todos commands e events:

x-event-id (UUID do evento/command)

x-event-type (tipo lógico, ex: inventory.reserve, payment.authorize, order.placed)

x-occurred-at (ISO UTC)

x-correlation-id (id do fluxo do checkout, estável)

x-causation-id (eventId que causou isso)

x-saga-id (UUID da saga do pedido)

x-saga-name = checkout

x-saga-step (passo atual)

x-aggregate-type / x-aggregate-id (ex: Order / orderId)

x-schema-version=v1 + x-topic-version=v1

content-type=application/json

Você já tem isso bem encaminhado no ms-order. O orquestrador vira o “dono” do x-saga-*.

Happy path (o fluxo que mais parece “vida real” e é fácil de evoluir)
0) Entrada

O usuário cria pedido no ms-order via REST/Postman.

ms-order publica no order.events.v1 com header:

x-event-type = order.placed

payload: orderId, customerId, items, etc.

1) Start Saga

Orquestrador consome order.events.v1 e vê x-event-type=order.placed.

Ele cria/atualiza o registro checkout_saga:

status=RUNNING

step=INVENTORY_RESERVE_PENDING

guarda sagaId, correlationId, lastEventId etc.

Orquestrador grava na outbox do próprio serviço um command:

inventory.reserve → publica em inventory.commands.v1

2) Inventory

ms-inventory consome inventory.commands.v1 (x-event-type=inventory.reserve)

Ele tenta reservar estoque (por SKU).

Ele publica em inventory.events.v1:

sucesso: inventory.reserved

falha: inventory.rejected

3) Payment

Orquestrador consome inventory.events.v1:

Se inventory.reserved:

atualiza saga: step=PAYMENT_AUTHORIZE_PENDING

publica command payment.authorize → payment.commands.v1

Se inventory.rejected:

vai pra compensação (cancelar pedido)

ms-payment consome payment.commands.v1 (payment.authorize)

Integra com Stripe (dev), tenta autorizar/confirmar.

Publica em payment.events.v1:

payment.authorized (ou payment.approved)

payment.declined / payment.failed

4) Shipping / Invoice / Notification (versão completa)

Orquestrador consome payment.events.v1:

Se payment.authorized:

shipping.create → shipping.commands.v1

Se payment.declined:

compensação: inventory.release + order.cancel

Shipping publica:

shipping.created / shipping.failed

Invoice publica:

invoice.issued / invoice.failed

Notification publica:

notification.sent / notification.failed

5) Finalização no Order

Quando todos passos “core” passaram, o orquestrador manda:

order.complete (command) → order.commands.v1

ms-order consome e muda estado interno + publica:

order.completed em order.events.v1

Se você preferir, pode existir order.confirmed antes e order.completed no final. Mas o importante é: o orquestrador manda o comando, e o ms-order é o dono do estado do pedido.

Máquina de estados da Saga (orquestrador)
Status gerais

RUNNING

COMPENSATING

COMPLETED

FAILED (quando estoura timeout/retry e não tem o que fazer)

CANCELLED (equivalente ao fim “com cancelamento”)

Steps (bem profissional e explícito)

Início

STARTED (ou cria no primeiro evento)

Happy path

INVENTORY_RESERVE_PENDING

PAYMENT_AUTHORIZE_PENDING

SHIPPING_CREATE_PENDING (opcional no MVP)

INVOICE_ISSUE_PENDING (opcional)

NOTIFICATION_SEND_PENDING (opcional)

ORDER_COMPLETE_PENDING

DONE

Compensação (rollback)

COMPENSATE_PAYMENT (refund/cancel)

COMPENSATE_INVENTORY (release)

COMPENSATE_ORDER (cancel)

COMPENSATED_DONE

Transições (regras claras)

Ao receber order.placed:

STARTED → INVENTORY_RESERVE_PENDING

Ao receber inventory.reserved:

INVENTORY_RESERVE_PENDING → PAYMENT_AUTHORIZE_PENDING

Ao receber inventory.rejected:

INVENTORY_RESERVE_PENDING → COMPENSATE_ORDER

Ao receber payment.authorized:

PAYMENT_AUTHORIZE_PENDING → ORDER_COMPLETE_PENDING (MVP)

ou → SHIPPING_CREATE_PENDING (versão completa)

Ao receber payment.declined:

PAYMENT_AUTHORIZE_PENDING → COMPENSATE_INVENTORY → COMPENSATE_ORDER

Ao receber order.completed:

ORDER_COMPLETE_PENDING → DONE (COMPLETED)

Timeout/retry (parte “empresa grande”)

Cada step tem:

nextAttemptAt / retryCount

se estourar limite:

vai pra FAILED ou entra em compensação dependendo do ponto

E idempotência por x-event-id (tabela processed_events) pra não avançar step duas vezes.

Microserviços futuros (o que recebem, o que fazem, o que publicam)

Abaixo eu vou colocar o “contrato mental” de cada um, já alinhado com seu mundo (Kafka, outbox, headers).

1) ms-order (já pronto)

Recebe

REST: POST /orders (place)

Kafka (opcional, recomendado): order.commands.v1

order.cancel

order.complete

(opcional) order.confirm

Publica

order.events.v1:

order.placed

order.confirmed (se você quiser separar)

order.canceled

order.completed

Ele espera

Que o orquestrador seja o “coordenador”, mas Order continua dono do estado.

2) ms-inventory (simples por SKU, como você decidiu)

Recebe

inventory.commands.v1:

inventory.reserve (orderId + itens sku/qty)

inventory.release (compensação)

Faz

Reserva/baixa (ou só “reserva” no MVP) com idempotência por orderId.

Publica

inventory.events.v1:

inventory.reserved

inventory.rejected

inventory.released

Ele espera

Key = orderId

x-correlation-id, x-saga-id, x-causation-id bem preenchidos.

3) ms-payment (Stripe dev)

Recebe

payment.commands.v1:

payment.authorize

payment.cancel (se autorizado mas precisa cancelar)

payment.refund (se capturado/pago e precisa estornar)

Faz

Autoriza/captura/estorna (você escolhe o nível no MVP)

Publica

payment.events.v1:

payment.authorized

payment.declined

payment.refunded / payment.canceled

Ele espera

x-correlation-id estável pra rastrear a jornada.

4) ms-shipping (envio)

Recebe

shipping.commands.v1:

shipping.create

shipping.cancel (compensação)

Faz

Cria uma “shipment” (no MVP pode ser só registro)

Integração real depois (transportadora etc.)

Publica

shipping.events.v1:

shipping.created

shipping.failed

shipping.canceled

5) ms-invoice (nota fiscal)

Recebe

invoice.commands.v1:

invoice.issue

invoice.cancel (se suportar)

Faz

Emite nota (no MVP pode ser “simulada” via Jasper)

Envia PDF por email ou deixa pronto pra notification

Publica

invoice.events.v1:

invoice.issued

invoice.failed

invoice.canceled (opcional)

Ele espera

Normalmente só roda depois de pagamento confirmado e shipping definido (depende do país/regra, mas no MVP seguimos essa ordem).

6) ms-notification (email/sms/push)

Recebe

notification.commands.v1:

notification.send

Faz

Envia email (ex: pedido confirmado, NF anexada, tracking etc.)

Publica

notification.events.v1:

notification.sent

notification.failed

Ele espera

Que notification.send traga o que ele precisa (to, template, links/attachments) ou IDs para buscar.

7) ms-product / ms-catalog (seu domínio de produto/categoria)

Recebe

REST (principalmente)

(opcional) eventos pra atualizar projeções/busca

Publica

product.events.v1 (opcional):

product.created, product.updated, etc.

No seu desenho, ms-order já carrega itens com productId/price; em empresa grande, order costuma “congelar” o preço no momento da compra (você já está no caminho certo com unitPrice no evento).

8) ms-customer (seu domínio de customer/address)

Recebe

REST (principal)

(opcional) eventos de cadastro/alteração

Publica

customer.events.v1 (opcional):

customer.created, customer.updated

MVP recomendado (pra começar o orquestrador sem explodir complexidade)

Eu faria assim:

MVP do orquestrador

Inputs: order.placed (de order.events.v1) + inventory.reserved/rejected + payment.authorized/declined

Outputs: inventory.reserve + inventory.release + payment.authorize + order.complete + order.cancel

Sem shipping/invoice/notification ainda.

Quando isso estiver redondo, você pluga os próximos passos sem refatorar tudo.

Se você quiser, no próximo passo eu te entrego a lista exata de event-types e command-types (com nomes finais) e o diagrama “texto” da saga (tipo um sequence diagram em markdown) pra você colar no README do orquestrador.


Perfeito — aqui vai a lista exata (final) de event-types e command-types + um sequence diagram em Markdown + a máquina de estados textual do orquestrador (MVP primeiro, depois extensão).

1) Convenções finais
   Tópicos (fixos)

Events

order.events.v1

inventory.events.v1

payment.events.v1

Commands

order.commands.v1

inventory.commands.v1

payment.commands.v1

Key sempre = orderId (String).

Headers obrigatórios (em tudo)

x-event-id (UUID)

x-event-type (abaixo)

x-occurred-at (ISO UTC)

x-correlation-id (id do checkout; estável)

x-causation-id (eventId que gerou este)

x-saga-id (UUID por order)

x-saga-name=checkout

x-saga-step (step do orquestrador)

x-aggregate-type / x-aggregate-id

x-schema-version=v1

x-topic-version=v1

content-type=application/json

2) EVENT TYPES (o que os serviços publicam)
   ms-order → order.events.v1

order.placed

order.completed

order.canceled

(opcional no futuro)

order.confirmed (se você quiser confirmar antes do completed)

ms-inventory → inventory.events.v1

inventory.reserved

inventory.rejected

inventory.released

ms-payment → payment.events.v1

payment.authorized

payment.declined

payment.canceled (quando cancelou uma autorização)

payment.refunded (se você capturar/pagar e depois estornar)

3) COMMAND TYPES (o que o orquestrador manda)
   orquestrador → inventory.commands.v1

inventory.reserve

inventory.release (compensação)

orquestrador → payment.commands.v1

payment.authorize

payment.cancel (compensação para autorização)

payment.refund (compensação após captura)

orquestrador → order.commands.v1

order.complete

order.cancel


4) MVP — Sequence Diagram (Markdown)

Cole isso no README do orquestrador:

sequenceDiagram
autonumber

    participant Client as Client(Postman)
    participant Order as ms-order
    participant Orq as ms-checkout-orchestrator
    participant Inv as ms-inventory
    participant Pay as ms-payment
    participant Kafka as Kafka

    Client->>Order: POST /orders (place)
    Order->>Kafka: publish order.events.v1<br/>x-event-type=order.placed (key=orderId)

    Kafka-->>Orq: consume order.events.v1<br/>order.placed
    Orq->>Orq: create saga(orderId)<br/>step=INVENTORY_RESERVE_PENDING
    Orq->>Kafka: publish inventory.commands.v1<br/>x-event-type=inventory.reserve

    Kafka-->>Inv: consume inventory.commands.v1<br/>inventory.reserve
    Inv->>Kafka: publish inventory.events.v1<br/>inventory.reserved OR inventory.rejected

    Kafka-->>Orq: consume inventory.events.v1
    alt inventory.reserved
        Orq->>Orq: step=PAYMENT_AUTHORIZE_PENDING
        Orq->>Kafka: publish payment.commands.v1<br/>payment.authorize
        Kafka-->>Pay: consume payment.commands.v1
        Pay->>Kafka: publish payment.events.v1<br/>payment.authorized OR payment.declined

        Kafka-->>Orq: consume payment.events.v1
        alt payment.authorized
            Orq->>Orq: step=ORDER_COMPLETE_PENDING
            Orq->>Kafka: publish order.commands.v1<br/>order.complete
            Kafka-->>Order: consume order.commands.v1<br/>order.complete
            Order->>Kafka: publish order.events.v1<br/>order.completed
            Kafka-->>Orq: consume order.events.v1<br/>order.completed
            Orq->>Orq: saga COMPLETED
        else payment.declined
            Orq->>Orq: step=COMPENSATE_INVENTORY
            Orq->>Kafka: publish inventory.commands.v1<br/>inventory.release
            Orq->>Kafka: publish order.commands.v1<br/>order.cancel
            Orq->>Orq: saga CANCELLED
        end
    else inventory.rejected
        Orq->>Kafka: publish order.commands.v1<br/>order.cancel
        Orq->>Orq: saga CANCELLED
    end

5) Máquina de estados do orquestrador (MVP)
   Status (alto nível)

RUNNING

COMPLETED

CANCELLED

COMPENSATING (opcional, mas recomendo registrar)

FAILED (timeout/retry estourou)

Steps (MVP)

STARTED

INVENTORY_RESERVE_PENDING

PAYMENT_AUTHORIZE_PENDING

ORDER_COMPLETE_PENDING

DONE

Steps (compensação MVP)

COMPENSATE_INVENTORY_RELEASE_PENDING

COMPENSATE_ORDER_CANCEL_PENDING

Transições (MVP)

order.placed:

STARTED → INVENTORY_RESERVE_PENDING + emite inventory.reserve

inventory.reserved:

INVENTORY_RESERVE_PENDING → PAYMENT_AUTHORIZE_PENDING + emite payment.authorize

inventory.rejected:

INVENTORY_RESERVE_PENDING → COMPENSATE_ORDER_CANCEL_PENDING + emite order.cancel → status CANCELLED

payment.authorized:

PAYMENT_AUTHORIZE_PENDING → ORDER_COMPLETE_PENDING + emite order.complete

payment.declined:

PAYMENT_AUTHORIZE_PENDING → COMPENSATE_INVENTORY_RELEASE_PENDING + emite inventory.release

depois: COMPENSATE_ORDER_CANCEL_PENDING + emite order.cancel → status CANCELLED

order.completed:

ORDER_COMPLETE_PENDING → DONE → status COMPLETED

Idempotência: qualquer evento repetido (mesmo x-event-id) deve ser ignorado (tabela processed_events).

6) Extensão futura (quando você plugar shipping/invoice/notification)

Você só adiciona steps e commands:

Commands

shipping.create / shipping.cancel

invoice.issue / invoice.cancel (se fizer sentido)

notification.send

Events

shipping.created / shipping.failed / shipping.canceled

invoice.issued / invoice.failed

notification.sent / notification.failed

Steps

SHIPPING_CREATE_PENDING

INVOICE_ISSUE_PENDING

NOTIFICATION_SEND_PENDING

E a finalização continua: order.complete.

Se você quiser, eu também posso te passar o contrato mínimo de payload para cada command/event do MVP (campos exatos), pra você já gerar os record ...V1 e os mappers no padrão do ms-order.


Fechado. Vou te dar os payloads mínimos (campos exatos) para cada command/event do MVP, no estilo que você já usa (records *V1, JSON simples), e alinhado com seus headers x-* (ou seja: o payload não precisa carregar eventType; isso vai no header).

Vou organizar por tópico.


1) order.events.v1 (events do ms-order)
   1.1 order.placed (event)




package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record OrderPlacedEventV1(
String eventId,
String occurredAt,
String orderId,
String customerId,
List<Item> items,
String discount // opcional (ex: "10.00") ou null
) {
public record Item(
String productId,
int quantity,
String unitPrice // string "59.90"
) {}
}



1.2 order.completed (event)

package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

public record OrderCompletedEventV1(
String eventId,
String occurredAt,
String orderId,
String customerId
) {}


1.3 order.canceled (event)

package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

public record OrderCanceledEventV1(
String eventId,
String occurredAt,
String orderId,
String customerId,
String reason // opcional, ex: "inventory.rejected" / "payment.declined"
) {}



2) order.commands.v1 (commands do orquestrador → ms-order)
   2.1 order.complete (command)

Key: orderId


package com.mvbr.retailstore.order.infrastructure.adapter.in.messaging.dto;

public record OrderCompleteCommandV1(
String commandId,
String occurredAt,
String orderId
) {}


2.2 order.cancel (command)

package com.mvbr.retailstore.order.infrastructure.adapter.in.messaging.dto;

public record OrderCancelCommandV1(
String commandId,
String occurredAt,
String orderId,
String reason // ex: "inventory.rejected" / "payment.declined"
) {}


Sim: commandId é o “eventId” do command. Mantém simetria. Header x-event-id vai ter o mesmo valor.

3) inventory.commands.v1 (orquestrador → ms-inventory)
   3.1 inventory.reserve (command)

Key: orderId

package com.mvbr.retailstore.inventory.infrastructure.adapter.in.messaging.dto;

import java.util.List;

public record InventoryReserveCommandV1(
String commandId,
String occurredAt,
String orderId,
List<Line> lines
) {
public record Line(
String sku,
int quantity
) {}
}


Use sku como padrão para estoque. No teu Order você usa productId; no orquestrador você pode mapear productId → sku (no MVP, considere que são iguais).

3.2 inventory.release (command) – compensação

package com.mvbr.retailstore.inventory.infrastructure.adapter.in.messaging.dto;

public record InventoryReleaseCommandV1(
String commandId,
String occurredAt,
String orderId
) {}


4) inventory.events.v1 (ms-inventory → mundo)
   4.1 inventory.reserved (event)

Key: orderId

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryReservedEventV1(
String eventId,
String occurredAt,
String orderId
) {}


4.2 inventory.rejected (event)

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryRejectedEventV1(
String eventId,
String occurredAt,
String orderId,
String reason // ex: "insufficient_stock"
) {}


4.3 inventory.released (event)

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryReleasedEventV1(
String eventId,
String occurredAt,
String orderId
) {}


5) payment.commands.v1 (orquestrador → ms-payment)
   5.1 payment.authorize (command)

Key: orderId

package com.mvbr.retailstore.payment.infrastructure.adapter.in.messaging.dto;

public record PaymentAuthorizeCommandV1(
String commandId,
String occurredAt,
String orderId,
String customerId,
String amount,    // "289.60"
String currency   // "BRL" (ou "USD")
) {}



No MVP, o orquestrador calcula amount a partir do evento order.placed (somando itens - discount). Depois você pode puxar do Order Read Model, se quiser.


5.2 payment.cancel (command) – compensação (após autorizado)

package com.mvbr.retailstore.payment.infrastructure.adapter.in.messaging.dto;

public record PaymentCancelCommandV1(
String commandId,
String occurredAt,
String orderId
) {}


5.3 payment.refund (command) – compensação (se capturar/pagar)


package com.mvbr.retailstore.payment.infrastructure.adapter.in.messaging.dto;

public record PaymentRefundCommandV1(
String commandId,
String occurredAt,
String orderId,
String amount,   // opcional (refund parcial)
String currency
) {}


6) payment.events.v1 (ms-payment → mundo)
   6.1 payment.authorized (event)

Key: orderId


package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentAuthorizedEventV1(
String eventId,
String occurredAt,
String orderId,
String paymentId // id interno ou do stripe (dev)
) {}



6.2 payment.declined (event)

package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentDeclinedEventV1(
String eventId,
String occurredAt,
String orderId,
String reason // ex: "card_declined"
) {}


6.3 payment.canceled (event)

package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentCanceledEventV1(
String eventId,
String occurredAt,
String orderId,
String paymentId
) {}


6.4 payment.refunded (event)

package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentRefundedEventV1(
String eventId,
String occurredAt,
String orderId,
String paymentId,
String amount,
String currency
) {}


7) Contrato mínimo do Orquestrador (o que ele precisa salvar no banco)

Pra coordenar só o MVP, ele precisa persistir:

orderId (unique)

sagaId (UUID)

status (RUNNING/COMPLETED/CANCELLED/COMPENSATING/FAILED)

step (INVENTORY_RESERVE_PENDING, PAYMENT_AUTHORIZE_PENDING, ORDER_COMPLETE_PENDING, …)

correlationId

lastProcessedEventId (ou tabela processed_events)

timestamps e versão

8) Regras de ouro (pra isso funcionar sem dor)

Payload pequeno, headers carregam metadados (tipo de evento, correlação, saga).

Idempotência por x-event-id (tabela processed_events).

Outbox no orquestrador também (senão saga “anda” sem publicar command).

Key = orderId em tudo (garante ordenação por pedido).

Se você quiser, eu te mando já o “EventEnvelope” padrão (um POJO/record que lê x-event-type, x-event-id, etc. dos headers) + um “router” switch(eventType) pro orquestrador. Isso acelera MUITO a implementação.

qual vai ser o nome do orquestrador?

Eu iria com:

ms-checkout-orchestrator

Por quê (bem “empresa grande”):

descreve exatamente o que ele faz: orquestrar o checkout

evita o nome genérico saga-orchestrator (que vira “lixeira” de tudo)

quando você criar outras sagas no futuro (ex: returns, refunds, replenishment), você não mistura tudo num único serviço

Alternativas boas (se você quiser outro estilo)

ms-checkout-saga (curto e claro)

ms-order-orchestrator (menos preciso; parece que orquestra só Order)

ms-fulfillment-orchestrator (mais “logística”, bom se o foco for shipping/invoice)

Minha recomendação final: ms-checkout-orchestrator.

Se você topar esse nome, eu sigo usando ele em todos os packages, topics e headers (ex: x-producer=ms-checkout-orchestrator).

combinado vai ser:  ms-checkout-orchestrator

Fechado 🤝 ms-checkout-orchestrator vai ser o nome.

Sugestões finais pra você já criar o projeto “com cara de produção”:

1) spring.application.name

ms-checkout-orchestrator (igual ao nome do serviço)

2) Producer header (x-producer)

ms-checkout-orchestrator

3) Package base (padrão consistente com o seu)

Se você está usando:

com.mvbr.retailstore.order...

Então aqui:

com.mvbr.retailstore.checkoutorchestrator...
ou (mais limpo)

com.mvbr.retailstore.checkout.orchestrator...

Eu prefiro o segundo:

✅ com.mvbr.retailstore.checkout.orchestrator

4) Tópicos que esse serviço vai usar no MVP

Consome:

order.events.v1

inventory.events.v1

payment.events.v1

Publica (commands):

inventory.commands.v1

payment.commands.v1

order.commands.v1

Se você quiser, o próximo passo é eu te mandar o esqueleto de pastas + classes principais do ms-checkout-orchestrator (com JPA da saga + processed_events + outbox + relay + listeners + router por x-event-type).

Perfeito — abaixo está o esqueleto completo (pasta + classes principais) do ms-checkout-orchestrator, no mesmo estilo profissional do seu ms-order: Kafka + Postgres + Outbox + headers x-* + idempotência.

Vou te entregar:

Tree de pacotes

Classes principais (prontas pra colar)

DTOs mínimos (records V1) pro MVP

State machine (Saga) implementada com flags de “aguardando confirmações”

KafkaConfig criando tópicos (DEV)

application.yaml base (opcional, mas útil)


1) Estrutura de pastas sugerida

com/mvbr/retailstore/checkout/orchestrator
├── CheckoutOrchestratorApplication.java
├── config
│   └── KafkaConfig.java
├── domain
│   ├── model
│   │   ├── CheckoutSaga.java
│   │   ├── SagaStatus.java
│   │   └── SagaStep.java
│   └── exception
│       └── SagaDomainException.java
├── application
│   ├── port
│   │   └── out
│   │       ├── CheckoutSagaRepository.java
│   │       ├── CommandPublisher.java
│   │       └── ProcessedEventRepository.java
│   └── service
│       └── CheckoutSagaService.java
└── infrastructure
├── adapter
│   ├── in
│   │   └── messaging
│   │       ├── CheckoutEventsConsumer.java
│   │       └── envelope
│   │           └── EventEnvelope.java
│   └── out
│       ├── messaging
│       │   ├── TopicNames.java
│       │   ├── dto
│       │   │   ├── OrderPlacedEventV1.java
│       │   │   ├── InventoryReservedEventV1.java
│       │   │   ├── InventoryRejectedEventV1.java
│       │   │   ├── PaymentAuthorizedEventV1.java
│       │   │   ├── PaymentDeclinedEventV1.java
│       │   │   ├── InventoryReserveCommandV1.java
│       │   │   ├── InventoryReleaseCommandV1.java
│       │   │   ├── PaymentAuthorizeCommandV1.java
│       │   │   ├── OrderCompleteCommandV1.java
│       │   │   └── OrderCancelCommandV1.java
│       │   ├── headers
│       │   │   ├── HeaderNames.java
│       │   │   └── SagaHeaders.java
│       │   └── outbox
│       │       ├── OutboxCommandPublisherAdapter.java
│       │       ├── OutboxJpaRepository.java
│       │       ├── OutboxMessageJpaEntity.java
│       │       ├── OutboxRelay.java
│       │       └── OutboxRetentionJob.java
│       └── persistence
│           ├── CheckoutSagaJpaEntity.java
│           ├── ProcessedEventJpaEntity.java
│           ├── JpaCheckoutSagaSpringDataRepository.java
│           ├── JpaProcessedEventSpringDataRepository.java
│           ├── JpaCheckoutSagaRepositoryAdapter.java
│           └── JpaProcessedEventRepositoryAdapter.java



2) Código — App principal

package com.mvbr.retailstore.checkout.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CheckoutOrchestratorApplication {
public static void main(String[] args) {
SpringApplication.run(CheckoutOrchestratorApplication.class, args);
}
}


3) Domain — Saga (state machine “de verdade”)

SagaStatus.java

package com.mvbr.retailstore.checkout.orchestrator.domain.model;

public enum SagaStatus {
RUNNING,
COMPENSATING,
COMPLETED,
CANCELLED,
FAILED
}


SagaStep.java

package com.mvbr.retailstore.checkout.orchestrator.domain.model;

public enum SagaStep {
STARTED,

    INVENTORY_RESERVE_PENDING,
    PAYMENT_AUTHORIZE_PENDING,
    ORDER_COMPLETE_PENDING,

    COMPENSATE_INVENTORY_RELEASE_PENDING,
    COMPENSATE_ORDER_CANCEL_PENDING,

    WAITING_COMPENSATIONS,   // aguardando inventory.released + order.canceled
    DONE
}


SagaDomainException.java


package com.mvbr.retailstore.checkout.orchestrator.domain.exception;

public class SagaDomainException extends RuntimeException {
public SagaDomainException(String message) {
super(message);
}
}


CheckoutSaga.java

package com.mvbr.retailstore.checkout.orchestrator.domain.model;

import com.mvbr.retailstore.checkout.orchestrator.domain.exception.SagaDomainException;

import java.util.Objects;
import java.util.UUID;

public class CheckoutSaga {

    private final String orderId;
    private final String sagaId;
    private final String correlationId;

    private SagaStatus status;
    private SagaStep step;

    // dados úteis pro MVP (payment.authorize precisa)
    private String customerId;
    private String amount;
    private String currency;

    // flags para finalizar corretamente
    private boolean orderCompleted;
    private boolean inventoryReleased;
    private boolean orderCanceled;

    private CheckoutSaga(String orderId, String sagaId, String correlationId) {
        if (orderId == null || orderId.isBlank()) throw new SagaDomainException("orderId cannot be null/blank");
        if (sagaId == null || sagaId.isBlank()) throw new SagaDomainException("sagaId cannot be null/blank");
        if (correlationId == null || correlationId.isBlank()) throw new SagaDomainException("correlationId cannot be null/blank");

        this.orderId = orderId;
        this.sagaId = sagaId;
        this.correlationId = correlationId;

        this.status = SagaStatus.RUNNING;
        this.step = SagaStep.STARTED;
        this.currency = "BRL"; // default (ajuste como quiser)
    }

    public static CheckoutSaga start(String orderId, String correlationId) {
        return new CheckoutSaga(orderId, UUID.randomUUID().toString(), correlationId);
    }

    // =========================================================
    // Eventos (inputs) -> transições (state machine)
    // =========================================================

    public void onOrderPlaced(String customerId, String amount, String currency) {
        ensureRunning();
        if (this.step != SagaStep.STARTED) {
            // idempotência por processed_events é o que segura;
            // aqui só evitamos regressão
            return;
        }

        this.customerId = required(customerId, "customerId");
        this.amount = required(amount, "amount");
        if (currency != null && !currency.isBlank()) this.currency = currency;

        this.step = SagaStep.INVENTORY_RESERVE_PENDING;
    }

    public void onInventoryReserved() {
        ensureRunning();
        ensureStep(SagaStep.INVENTORY_RESERVE_PENDING);
        this.step = SagaStep.PAYMENT_AUTHORIZE_PENDING;
    }

    public void onInventoryRejected() {
        ensureRunning();
        // se rejeitar, já vai pra compensação (cancelar order)
        this.status = SagaStatus.COMPENSATING;
        this.step = SagaStep.COMPENSATE_ORDER_CANCEL_PENDING;
    }

    public void onPaymentAuthorized() {
        ensureRunning();
        ensureStep(SagaStep.PAYMENT_AUTHORIZE_PENDING);
        this.step = SagaStep.ORDER_COMPLETE_PENDING;
    }

    public void onPaymentDeclined() {
        ensureRunning();
        ensureStep(SagaStep.PAYMENT_AUTHORIZE_PENDING);
        this.status = SagaStatus.COMPENSATING;
        this.step = SagaStep.COMPENSATE_INVENTORY_RELEASE_PENDING;
    }

    public void markOrderCompleted() {
        if (status != SagaStatus.RUNNING) return;
        this.orderCompleted = true;
        this.status = SagaStatus.COMPLETED;
        this.step = SagaStep.DONE;
    }

    public void markInventoryReleased() {
        this.inventoryReleased = true;
        tryFinishCompensation();
    }

    public void markOrderCanceled() {
        this.orderCanceled = true;
        tryFinishCompensation();
    }

    private void tryFinishCompensation() {
        if (status != SagaStatus.COMPENSATING) return;

        // no MVP: só finaliza cancelado quando os dois chegaram
        if (inventoryReleased && orderCanceled) {
            this.status = SagaStatus.CANCELLED;
            this.step = SagaStep.DONE;
        } else {
            this.step = SagaStep.WAITING_COMPENSATIONS;
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void ensureRunning() {
        if (status == SagaStatus.COMPLETED || status == SagaStatus.CANCELLED) {
            // saga já finalizada: ignora eventos atrasados
            return;
        }
    }

    private void ensureStep(SagaStep expected) {
        if (this.step != expected) {
            throw new SagaDomainException("Invalid step transition: expected " + expected + " but was " + step);
        }
    }

    private String required(String v, String name) {
        if (v == null || v.isBlank()) throw new SagaDomainException(name + " cannot be null/blank");
        return v;
    }

    // =========================================================
    // Getters
    // =========================================================

    public String getOrderId() { return orderId; }
    public String getSagaId() { return sagaId; }
    public String getCorrelationId() { return correlationId; }
    public SagaStatus getStatus() { return status; }
    public SagaStep getStep() { return step; }
    public String getCustomerId() { return customerId; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public boolean isOrderCompleted() { return orderCompleted; }
    public boolean isInventoryReleased() { return inventoryReleased; }
    public boolean isOrderCanceled() { return orderCanceled; }

    // =========================================================
    // Restore (para persistência)
    // =========================================================

    public static CheckoutSaga restore(
            String orderId,
            String sagaId,
            String correlationId,
            SagaStatus status,
            SagaStep step,
            String customerId,
            String amount,
            String currency,
            boolean orderCompleted,
            boolean inventoryReleased,
            boolean orderCanceled
    ) {
        CheckoutSaga saga = new CheckoutSaga(orderId, sagaId, correlationId);
        saga.status = Objects.requireNonNull(status);
        saga.step = Objects.requireNonNull(step);
        saga.customerId = customerId;
        saga.amount = amount;
        saga.currency = (currency == null || currency.isBlank()) ? "BRL" : currency;
        saga.orderCompleted = orderCompleted;
        saga.inventoryReleased = inventoryReleased;
        saga.orderCanceled = orderCanceled;
        return saga;
    }
}


4) Application Ports + Service (core do orquestrador)

Ports

package com.mvbr.retailstore.checkout.orchestrator.application.port.out;

import com.mvbr.retailstore.checkout.orchestrator.domain.model.CheckoutSaga;

import java.util.Optional;

public interface CheckoutSagaRepository {
void save(CheckoutSaga saga);
Optional<CheckoutSaga> findByOrderId(String orderId);

    default CheckoutSaga getByOrderId(String orderId) {
        return findByOrderId(orderId).orElseThrow(() -> new IllegalArgumentException("Saga not found orderId=" + orderId));
    }
}



package com.mvbr.retailstore.checkout.orchestrator.application.port.out;

public interface ProcessedEventRepository {
boolean alreadyProcessed(String eventId);
void markProcessed(String eventId, String eventType, String orderId);
}



package com.mvbr.retailstore.checkout.orchestrator.application.port.out;

import java.util.Map;

public interface CommandPublisher {
void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers);
}



Service (router do MVP + regras)

package com.mvbr.retailstore.checkout.orchestrator.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.orchestrator.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.orchestrator.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.orchestrator.application.port.out.ProcessedEventRepository;
import com.mvbr.retailstore.checkout.orchestrator.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.in.messaging.envelope.EventEnvelope;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto.*;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutSagaService {

    private final CheckoutSagaRepository sagaRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final CommandPublisher commandPublisher;
    private final ObjectMapper objectMapper;

    public CheckoutSagaService(CheckoutSagaRepository sagaRepository,
                              ProcessedEventRepository processedEventRepository,
                              CommandPublisher commandPublisher,
                              ObjectMapper objectMapper) {
        this.sagaRepository = sagaRepository;
        this.processedEventRepository = processedEventRepository;
        this.commandPublisher = commandPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(EventEnvelope env) {
        if (processedEventRepository.alreadyProcessed(env.eventId())) return;

        switch (env.eventType()) {

            case "order.placed" -> onOrderPlaced(env);

            case "inventory.reserved" -> onInventoryReserved(env);

            case "inventory.rejected" -> onInventoryRejected(env);

            case "payment.authorized" -> onPaymentAuthorized(env);

            case "payment.declined" -> onPaymentDeclined(env);

            case "order.completed" -> onOrderCompleted(env);

            case "inventory.released" -> onInventoryReleased(env);

            case "order.canceled" -> onOrderCanceled(env);

            default -> {
                // ignore silencioso ou log no consumer
            }
        }

        processedEventRepository.markProcessed(env.eventId(), env.eventType(), env.aggregateIdOrKey());
    }

    private void onOrderPlaced(EventEnvelope env) {
        OrderPlacedEventV1 placed = env.readPayload(objectMapper, OrderPlacedEventV1.class);

        String orderId = placed.orderId();
        String correlationId = env.correlationIdOr(orderId);

        CheckoutSaga saga = sagaRepository.findByOrderId(orderId)
                .orElseGet(() -> CheckoutSaga.start(orderId, correlationId));

        // calcula amount no MVP (soma items - discount)
        BigDecimal subtotal = placed.items().stream()
                .map(i -> new BigDecimal(i.unitPrice()).multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = (placed.discount() == null || placed.discount().isBlank())
                ? BigDecimal.ZERO
                : new BigDecimal(placed.discount());

        BigDecimal total = subtotal.subtract(discount);
        if (total.signum() < 0) total = BigDecimal.ZERO;

        saga.onOrderPlaced(placed.customerId(), total.toPlainString(), "BRL");
        sagaRepository.save(saga);

        // command: inventory.reserve
        InventoryReserveCommandV1 cmd = new InventoryReserveCommandV1(
                newCommandId(), now(),
                orderId,
                placed.items().stream()
                        .map(i -> new InventoryReserveCommandV1.Line(i.productId(), i.quantity()))
                        .toList()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),                  // causationId = eventId do order.placed
                "checkout",
                "INVENTORY_RESERVE_PENDING",
                "Order",
                orderId
        );

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                orderId,
                "inventory.reserve",
                cmd,
                headers
        );
    }

    private void onInventoryReserved(EventEnvelope env) {
        InventoryReservedEventV1 e = env.readPayload(objectMapper, InventoryReservedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(e.orderId());

        saga.onInventoryReserved();
        sagaRepository.save(saga);

        PaymentAuthorizeCommandV1 cmd = new PaymentAuthorizeCommandV1(
                newCommandId(), now(),
                saga.getOrderId(),
                saga.getCustomerId(),
                saga.getAmount(),
                saga.getCurrency()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "PAYMENT_AUTHORIZE_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.PAYMENT_COMMANDS_V1,
                saga.getOrderId(),
                "payment.authorize",
                cmd,
                headers
        );
    }

    private void onInventoryRejected(EventEnvelope env) {
        InventoryRejectedEventV1 e = env.readPayload(objectMapper, InventoryRejectedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(e.orderId());

        saga.onInventoryRejected();
        sagaRepository.save(saga);

        OrderCancelCommandV1 cmd = new OrderCancelCommandV1(
                newCommandId(), now(),
                saga.getOrderId(),
                "inventory.rejected:" + e.reason()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "COMPENSATE_ORDER_CANCEL_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.cancel",
                cmd,
                headers
        );
    }

    private void onPaymentAuthorized(EventEnvelope env) {
        PaymentAuthorizedEventV1 e = env.readPayload(objectMapper, PaymentAuthorizedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(e.orderId());

        saga.onPaymentAuthorized();
        sagaRepository.save(saga);

        OrderCompleteCommandV1 cmd = new OrderCompleteCommandV1(
                newCommandId(), now(),
                saga.getOrderId()
        );

        Map<String, String> headers = SagaHeaders.forCommand(
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "ORDER_COMPLETE_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.complete",
                cmd,
                headers
        );
    }

    private void onPaymentDeclined(EventEnvelope env) {
        PaymentDeclinedEventV1 e = env.readPayload(objectMapper, PaymentDeclinedEventV1.class);
        CheckoutSaga saga = sagaRepository.getByOrderId(e.orderId());

        saga.onPaymentDeclined();
        sagaRepository.save(saga);

        // 1) inventory.release
        InventoryReleaseCommandV1 release = new InventoryReleaseCommandV1(
                newCommandId(), now(), saga.getOrderId()
        );

        Map<String, String> h1 = SagaHeaders.forCommand(
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "COMPENSATE_INVENTORY_RELEASE_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.INVENTORY_COMMANDS_V1,
                saga.getOrderId(),
                "inventory.release",
                release,
                h1
        );

        // 2) order.cancel
        OrderCancelCommandV1 cancel = new OrderCancelCommandV1(
                newCommandId(), now(), saga.getOrderId(), "payment.declined:" + e.reason()
        );

        Map<String, String> h2 = SagaHeaders.forCommand(
                saga.getSagaId(),
                saga.getCorrelationId(),
                env.eventId(),
                "checkout",
                "COMPENSATE_ORDER_CANCEL_PENDING",
                "Order",
                saga.getOrderId()
        );

        commandPublisher.publish(
                TopicNames.ORDER_COMMANDS_V1,
                saga.getOrderId(),
                "order.cancel",
                cancel,
                h2
        );
    }

    private void onOrderCompleted(EventEnvelope env) {
        // payload pode ser simples, mas aqui só precisamos do orderId via key/aggr
        String orderId = env.aggregateIdOrKey();
        CheckoutSaga saga = sagaRepository.getByOrderId(orderId);
        saga.markOrderCompleted();
        sagaRepository.save(saga);
    }

    private void onInventoryReleased(EventEnvelope env) {
        String orderId = env.aggregateIdOrKey();
        CheckoutSaga saga = sagaRepository.getByOrderId(orderId);
        saga.markInventoryReleased();
        sagaRepository.save(saga);
    }

    private void onOrderCanceled(EventEnvelope env) {
        String orderId = env.aggregateIdOrKey();
        CheckoutSaga saga = sagaRepository.getByOrderId(orderId);
        saga.markOrderCanceled();
        sagaRepository.save(saga);
    }

    private String newCommandId() {
        return UUID.randomUUID().toString();
    }

    private String now() {
        return Instant.now().toString();
    }
}


5) Infra — TopicNames

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging;

public final class TopicNames {

    private TopicNames() {}

    // Events
    public static final String ORDER_EVENTS_V1 = "order.events.v1";
    public static final String INVENTORY_EVENTS_V1 = "inventory.events.v1";
    public static final String PAYMENT_EVENTS_V1 = "payment.events.v1";

    // Commands
    public static final String ORDER_COMMANDS_V1 = "order.commands.v1";
    public static final String INVENTORY_COMMANDS_V1 = "inventory.commands.v1";
    public static final String PAYMENT_COMMANDS_V1 = "payment.commands.v1";
}



6) Consumer — lê headers x-event-type e entrega EventEnvelope

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.in.messaging.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record EventEnvelope(
String topic,
String key,
String payloadJson,
String eventId,
String eventType,
String occurredAt,
String correlationId,
String causationId,
String sagaId,
String sagaName,
String sagaStep,
String aggregateType,
String aggregateId
) {
public static EventEnvelope from(ConsumerRecord<String, String> r) {
return new EventEnvelope(
r.topic(),
r.key(),
r.value(),
header(r, HeaderNames.EVENT_ID).orElse(""),
header(r, HeaderNames.EVENT_TYPE).orElse(""),
header(r, HeaderNames.OCCURRED_AT).orElse(""),
header(r, HeaderNames.CORRELATION_ID).orElse(""),
header(r, HeaderNames.CAUSATION_ID).orElse(""),
header(r, HeaderNames.SAGA_ID).orElse(""),
header(r, HeaderNames.SAGA_NAME).orElse(""),
header(r, HeaderNames.SAGA_STEP).orElse(""),
header(r, HeaderNames.AGGREGATE_TYPE).orElse(""),
header(r, HeaderNames.AGGREGATE_ID).orElse(r.key())
);
}

    private static Optional<String> header(ConsumerRecord<String, String> r, String name) {
        Header h = r.headers().lastHeader(name);
        if (h == null) return Optional.empty();
        String v = new String(h.value(), StandardCharsets.UTF_8);
        return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v);
    }

    public String aggregateIdOrKey() {
        return (aggregateId != null && !aggregateId.isBlank()) ? aggregateId : key;
    }

    public String correlationIdOr(String fallback) {
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : fallback;
    }

    public <T> T readPayload(ObjectMapper mapper, Class<T> clazz) {
        try {
            return mapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse payload as " + clazz.getSimpleName() + " topic=" + topic + " key=" + key, e);
        }
    }
}



CheckoutEventsConsumer.java

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.in.messaging;

import com.mvbr.retailstore.checkout.orchestrator.application.service.CheckoutSagaService;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.in.messaging.envelope.EventEnvelope;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.TopicNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class CheckoutEventsConsumer {

    private static final Logger log = Logger.getLogger(CheckoutEventsConsumer.class.getName());

    private final CheckoutSagaService sagaService;

    public CheckoutEventsConsumer(CheckoutSagaService sagaService) {
        this.sagaService = sagaService;
    }

    @KafkaListener(
            topics = {
                    TopicNames.ORDER_EVENTS_V1,
                    TopicNames.INVENTORY_EVENTS_V1,
                    TopicNames.PAYMENT_EVENTS_V1
            },
            groupId = "${kafka.consumer.group-id:ms-checkout-orchestrator}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope env = EventEnvelope.from(record);

        if (env.eventType() == null || env.eventType().isBlank()) {
            log.warning("Ignoring message without x-event-type. topic=" + env.topic() + " key=" + env.key());
            return;
        }

        sagaService.handle(env);
    }
}




7) Headers — HeaderNames + SagaHeaders (para commands)


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.headers;

public final class HeaderNames {

    private HeaderNames() {}

    public static final String EVENT_ID = "x-event-id";
    public static final String EVENT_TYPE = "x-event-type";
    public static final String OCCURRED_AT = "x-occurred-at";

    public static final String PRODUCER = "x-producer";
    public static final String SCHEMA_VERSION = "x-schema-version";
    public static final String TOPIC_VERSION = "x-topic-version";

    public static final String CORRELATION_ID = "x-correlation-id";
    public static final String CAUSATION_ID = "x-causation-id";

    public static final String TRACEPARENT = "traceparent";
    public static final String CONTENT_TYPE = "content-type";

    public static final String AGGREGATE_TYPE = "x-aggregate-type";
    public static final String AGGREGATE_ID = "x-aggregate-id";

    public static final String SAGA_ID = "x-saga-id";
    public static final String SAGA_NAME = "x-saga-name";
    public static final String SAGA_STEP = "x-saga-step";
}




SagaHeaders.java (sem MDC: orquestrador controla tudo explicitamente)

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.headers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SagaHeaders {

    private static final String PRODUCER = "ms-checkout-orchestrator";
    private static final String SCHEMA_VERSION = "v1";
    private static final String TOPIC_VERSION = "v1";
    private static final String CONTENT_TYPE = "application/json";

    private SagaHeaders() {}

    public static Map<String, String> forCommand(
            String sagaId,
            String correlationId,
            String causationId,
            String sagaName,
            String sagaStep,
            String aggregateType,
            String aggregateId
    ) {
        Map<String, String> h = new LinkedHashMap<>();

        // envelope básico
        h.put(HeaderNames.EVENT_ID, UUID.randomUUID().toString());
        // NOTE: x-event-type entra no OutboxPublisher (ele sabe qual commandType está publicando)
        h.put(HeaderNames.OCCURRED_AT, Instant.now().toString());

        h.put(HeaderNames.PRODUCER, PRODUCER);
        h.put(HeaderNames.SCHEMA_VERSION, SCHEMA_VERSION);
        h.put(HeaderNames.TOPIC_VERSION, TOPIC_VERSION);
        h.put(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);

        h.put(HeaderNames.CORRELATION_ID, (correlationId == null || correlationId.isBlank()) ? aggregateId : correlationId);
        h.put(HeaderNames.CAUSATION_ID, (causationId == null || causationId.isBlank()) ? aggregateId : causationId);

        if (aggregateType != null && !aggregateType.isBlank()) h.put(HeaderNames.AGGREGATE_TYPE, aggregateType);
        if (aggregateId != null && !aggregateId.isBlank()) h.put(HeaderNames.AGGREGATE_ID, aggregateId);

        if (sagaId != null && !sagaId.isBlank()) h.put(HeaderNames.SAGA_ID, sagaId);
        if (sagaName != null && !sagaName.isBlank()) h.put(HeaderNames.SAGA_NAME, sagaName);
        if (sagaStep != null && !sagaStep.isBlank()) h.put(HeaderNames.SAGA_STEP, sagaStep);

        return h;
    }
}


Repara: x-event-type do command eu vou setar no OutboxCommandPublisherAdapter (porque ele recebe commandType). Assim você nunca esquece.


8) Outbox (mesmo padrão do seu ms-order)
   OutboxMessageJpaEntity.java

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
name = "outbox_messages",
indexes = {
@Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
@Index(name = "idx_outbox_status_next_attempt", columnList = "status, next_attempt_at"),
@Index(name = "uk_outbox_event_id", columnList = "event_id", unique = true)
}
)
public class OutboxMessageJpaEntity {

    public enum Status { PENDING, IN_PROGRESS, PUBLISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Lob
    @Column(name = "headers_json", nullable = false)
    private String headersJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Version
    private long version;

    protected OutboxMessageJpaEntity() {}

    public OutboxMessageJpaEntity(String eventId,
                                  String aggregateType,
                                  String aggregateId,
                                  String eventType,
                                  String topic,
                                  String payloadJson,
                                  String headersJson,
                                  Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payloadJson = payloadJson;
        this.headersJson = headersJson;
        this.occurredAt = occurredAt;

        this.status = Status.PENDING.name();
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
        this.retryCount = 0;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPayloadJson() { return payloadJson; }
    public String getHeadersJson() { return headersJson; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    public void markInProgress() { this.status = Status.IN_PROGRESS.name(); }

    public void markPublished() {
        this.status = Status.PUBLISHED.name();
        this.publishedAt = Instant.now();
        this.lastError = null;
        this.nextAttemptAt = this.publishedAt;
    }

    public void markFailed(String error) {
        this.status = Status.FAILED.name();
        this.lastError = error;
        this.retryCount = this.retryCount + 1;
        this.nextAttemptAt = computeBackoff();
    }

    private Instant computeBackoff() {
        long baseSeconds = 5;
        long maxSeconds = 3600;
        long delaySeconds = (long) Math.min(maxSeconds, baseSeconds * Math.pow(2, Math.max(0, retryCount - 1)));
        return Instant.now().plusSeconds(delaySeconds);
    }
}


OutboxJpaRepository.java

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    List<OutboxMessageJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant nextAttemptAt);

    long deleteByStatusAndPublishedAtBefore(String status, Instant cutoff);
}



OutboxCommandPublisherAdapter.java


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.orchestrator.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Primary
@Component
public class OutboxCommandPublisherAdapter implements CommandPublisher {

    private static final String AGGREGATE_TYPE = "CheckoutSaga";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxCommandPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers) {
        String payloadJson = write(payload);

        // garante x-event-type sempre presente e correto
        Map<String, String> merged = new LinkedHashMap<>(headers);
        merged.put(HeaderNames.EVENT_TYPE, commandType);

        // garante x-event-id existir e ser consistente com o Outbox eventId
        String eventId = merged.get(HeaderNames.EVENT_ID);
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("headers must contain x-event-id");
        }

        String headersJson = write(merged);

        OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                eventId,
                AGGREGATE_TYPE,
                key,
                commandType,
                topic,
                payloadJson,
                headersJson,
                Instant.now()
        );

        outboxRepository.save(msg);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize value to JSON", e);
        }
    }
}


OutboxRelay.java


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = Logger.getLogger(OutboxRelay.class.getName());

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxJpaRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixedDelayMs:10000}")
    @Transactional
    public void tick() {
        List<OutboxMessageJpaEntity> pending =
                outboxRepository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(
                                OutboxMessageJpaEntity.Status.PENDING.name(),
                                OutboxMessageJpaEntity.Status.FAILED.name()
                        ),
                        Instant.now()
                );

        if (!pending.isEmpty()) {
            log.info("OutboxRelay tick - pending size: " + pending.size());
        }

        for (OutboxMessageJpaEntity msg : pending) {
            try {
                msg.markInProgress();

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        msg.getTopic(),
                        msg.getAggregateId(),
                        msg.getPayloadJson()
                );

                parseHeaders(msg).forEach((name, value) ->
                        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).get();
                msg.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                msg.markFailed(e.getMessage());
                throw new IllegalStateException("Thread interrupted while publishing outbox id=" + msg.getId(), e);
            } catch (ExecutionException | RuntimeException e) {
                log.warning("Outbox publish failed id=" + msg.getId() + " error=" + e.getMessage());
                msg.markFailed(e.getMessage());
            }
        }
    }

    private Map<String, String> parseHeaders(OutboxMessageJpaEntity msg) {
        try {
            return objectMapper.readValue(msg.getHeadersJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse headers for outbox id=" + msg.getId(), e);
        }
    }
}


OutboxRetentionJob.java

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "outbox.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetentionJob {

    private static final Logger log = Logger.getLogger(OutboxRetentionJob.class.getName());

    private final OutboxJpaRepository outboxRepository;

    public OutboxRetentionJob(OutboxJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "${outbox.retention.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupPublished() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        long deleted = outboxRepository.deleteByStatusAndPublishedAtBefore(
                OutboxMessageJpaEntity.Status.PUBLISHED.name(),
                cutoff
        );
        if (deleted > 0) {
            log.info("OutboxRetentionJob removed " + deleted + " published messages older than " + cutoff);
        }
    }
}


9) Persistência — Saga + Processed Events (idempotência)
   CheckoutSagaJpaEntity.java

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "checkout_saga", indexes = {
@Index(name = "uk_checkout_saga_order_id", columnList = "order_id", unique = true)
})
public class CheckoutSagaJpaEntity {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "step", nullable = false, length = 64)
    private String step;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "amount", length = 32)
    private String amount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "order_completed", nullable = false)
    private boolean orderCompleted;

    @Column(name = "inventory_released", nullable = false)
    private boolean inventoryReleased;

    @Column(name = "order_canceled", nullable = false)
    private boolean orderCanceled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CheckoutSagaJpaEntity() {}

    // getters/setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isOrderCompleted() { return orderCompleted; }
    public void setOrderCompleted(boolean orderCompleted) { this.orderCompleted = orderCompleted; }
    public boolean isInventoryReleased() { return inventoryReleased; }
    public void setInventoryReleased(boolean inventoryReleased) { this.inventoryReleased = inventoryReleased; }
    public boolean isOrderCanceled() { return orderCanceled; }
    public void setOrderCanceled(boolean orderCanceled) { this.orderCanceled = orderCanceled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


ProcessedEventJpaEntity.java

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {}

    public ProcessedEventJpaEntity(String eventId, String eventType, String orderId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
}


Spring Data repos + adapters

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCheckoutSagaSpringDataRepository extends JpaRepository<CheckoutSagaJpaEntity, String> {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedEventSpringDataRepository extends JpaRepository<ProcessedEventJpaEntity, String> {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.checkout.orchestrator.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.orchestrator.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.orchestrator.domain.model.SagaStatus;
import com.mvbr.retailstore.checkout.orchestrator.domain.model.SagaStep;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaCheckoutSagaRepositoryAdapter implements CheckoutSagaRepository {

    private final JpaCheckoutSagaSpringDataRepository repo;

    public JpaCheckoutSagaRepositoryAdapter(JpaCheckoutSagaSpringDataRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(CheckoutSaga saga) {
        CheckoutSagaJpaEntity e = new CheckoutSagaJpaEntity();
        e.setOrderId(saga.getOrderId());
        e.setSagaId(saga.getSagaId());
        e.setCorrelationId(saga.getCorrelationId());
        e.setStatus(saga.getStatus().name());
        e.setStep(saga.getStep().name());
        e.setCustomerId(saga.getCustomerId());
        e.setAmount(saga.getAmount());
        e.setCurrency(saga.getCurrency());
        e.setOrderCompleted(saga.isOrderCompleted());
        e.setInventoryReleased(saga.isInventoryReleased());
        e.setOrderCanceled(saga.isOrderCanceled());
        e.setUpdatedAt(Instant.now());
        repo.save(e);
    }

    @Override
    public Optional<CheckoutSaga> findByOrderId(String orderId) {
        return repo.findById(orderId).map(e ->
                CheckoutSaga.restore(
                        e.getOrderId(),
                        e.getSagaId(),
                        e.getCorrelationId(),
                        SagaStatus.valueOf(e.getStatus()),
                        SagaStep.valueOf(e.getStep()),
                        e.getCustomerId(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.isOrderCompleted(),
                        e.isInventoryReleased(),
                        e.isOrderCanceled()
                )
        );
    }
}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.checkout.orchestrator.application.port.out.ProcessedEventRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventSpringDataRepository repo;

    public JpaProcessedEventRepositoryAdapter(JpaProcessedEventSpringDataRepository repo) {
        this.repo = repo;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        return repo.existsById(eventId);
    }

    @Override
    public void markProcessed(String eventId, String eventType, String orderId) {
        repo.save(new ProcessedEventJpaEntity(eventId, eventType, orderId));
    }
}


10) KafkaConfig (criação de tópicos DEV + ProducerFactory Boot 3.4)

package com.mvbr.retailstore.checkout.orchestrator.config;

import com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.TopicNames;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Bean
    @ConditionalOnProperty(prefix = "kafka.topics", name = "autoCreate", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics orchestratorTopics(
            @Value("${kafka.topics.partitions:3}") int partitions,
            @Value("${kafka.topics.replicationFactor:1}") short replicationFactor
    ) {
        return new KafkaAdmin.NewTopics(
                topic(TopicNames.ORDER_COMMANDS_V1, partitions, replicationFactor),
                topic(TopicNames.INVENTORY_COMMANDS_V1, partitions, replicationFactor),
                topic(TopicNames.PAYMENT_COMMANDS_V1, partitions, replicationFactor),

                // opcional criar events em DEV (não atrapalha)
                topic(TopicNames.ORDER_EVENTS_V1, partitions, replicationFactor),
                topic(TopicNames.INVENTORY_EVENTS_V1, partitions, replicationFactor),
                topic(TopicNames.PAYMENT_EVENTS_V1, partitions, replicationFactor)
        );
    }

    private NewTopic topic(String name, int partitions, short replicationFactor) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties,
            ObjectProvider<SslBundles> sslBundlesProvider
    ) {
        SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
        Map<String, Object> props = kafkaProperties.buildProducerProperties(sslBundles);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}


11) DTOs mínimos (records V1) — MVP

Coloca em infrastructure/adapter/out/messaging/dto/:

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record OrderPlacedEventV1(
String eventId,
String occurredAt,
String orderId,
String customerId,
List<Item> items,
String discount
) {
public record Item(String productId, int quantity, String unitPrice) {}
}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record InventoryReservedEventV1(String eventId, String occurredAt, String orderId) {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record InventoryRejectedEventV1(String eventId, String occurredAt, String orderId, String reason) {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record PaymentAuthorizedEventV1(String eventId, String occurredAt, String orderId, String paymentId) {}



package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record PaymentDeclinedEventV1(String eventId, String occurredAt, String orderId, String reason) {}



Commands:

package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record InventoryReserveCommandV1(
String commandId,
String occurredAt,
String orderId,
List<Line> lines
) {
public record Line(String sku, int quantity) {}
}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record InventoryReleaseCommandV1(String commandId, String occurredAt, String orderId) {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record PaymentAuthorizeCommandV1(
String commandId,
String occurredAt,
String orderId,
String customerId,
String amount,
String currency
) {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record OrderCompleteCommandV1(String commandId, String occurredAt, String orderId) {}


package com.mvbr.retailstore.checkout.orchestrator.infrastructure.adapter.out.messaging.dto;

public record OrderCancelCommandV1(String commandId, String occurredAt, String orderId, String reason) {}



12) application.yaml base (bem parecido com o seu)

server:
port: ${SERVER_PORT:8097}

spring:
application:
name: ms-checkout-orchestrator

datasource:
url: ${DATASOURCE_URL:jdbc:postgresql://localhost:5432/ms-checkout-orchestrator}
username: ${DATASOURCE_USERNAME:postgres}
password: ${DATASOURCE_PASSWORD:postgres}
driver-class-name: org.postgresql.Driver

jpa:
hibernate:
ddl-auto: none
properties:
hibernate:
format_sql: true

kafka:
bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
producer:
key-serializer: org.apache.kafka.common.serialization.StringSerializer
value-serializer: org.apache.kafka.common.serialization.StringSerializer
acks: all
properties:
enable.idempotence: true
max.in.flight.requests.per.connection: 5
retries: 2147483647
delivery.timeout.ms: 120000
request.timeout.ms: 30000
compression.type: snappy
linger.ms: 5

kafka:
topics:
autoCreate: ${KAFKA_TOPICS_AUTO_CREATE:true}
partitions: ${KAFKA_TOPICS_PARTITIONS:3}
replicationFactor: ${KAFKA_TOPICS_REPLICATION_FACTOR:1}

kafka:
consumer:
group-id: ${KAFKA_CONSUMER_GROUP_ID:ms-checkout-orchestrator}

outbox:
relay:
enabled: ${OUTBOX_RELAY_ENABLED:true}
fixedDelayMs: ${OUTBOX_RELAY_FIXED_DELAY_MS:10000}
retention:
enabled: ${OUTBOX_RETENTION_ENABLED:true}
cron: ${OUTBOX_RETENTION_CRON:0 0 3 * * *}

logging:
level:
root: INFO
com.mvbr.retailstore.checkout.orchestrator: INFO
org.springframework.kafka: INFO
org.apache.kafka: WARN


Observação: aqui tem “kafka:” repetido em raiz (igual o teu caso anterior). Se você quiser, eu te mando a versão sem duplicar chave (fica mais limpo). Mas funciona do mesmo jeito? Não: YAML com chave repetida sobrescreve. Então no final a gente vai ajustar isso quando você for colar de verdade.



Próximo passo (bem direto)

Se você topar, eu te mando agora as migrations Flyway (DDL) para:

checkout_saga

processed_events

outbox_messages

E também a lista exata de listeners/consumers que cada microserviço (order/inventory/payment) vai precisar pra fechar o MVP.



Só me diz uma coisa rápida (sem enrolação): você vai criar o banco ms-checkout-orchestrator separado no Postgres (recomendado) ou vai usar o mesmo Postgres/schema do ms-order por enquanto?


resposta: vai ser postgres conforme esta configurado no application.yaml....   e o nome do banco sera:  ms-checkout-orchestrator


Se você topar, eu te mando agora as migrations Flyway (DDL) para:

checkout_saga

processed_events

outbox_messages

E também a lista exata de listeners/consumers que cada microserviço (order/inventory/payment) vai precisar pra fechar o MVP.


CREATE TABLE IF NOT EXISTS checkout_saga (
order_id            VARCHAR(64) PRIMARY KEY,
saga_id             VARCHAR(64)  NOT NULL,
correlation_id      VARCHAR(64)  NOT NULL,

    status              VARCHAR(32)  NOT NULL,
    step                VARCHAR(64)  NOT NULL,

    customer_id         VARCHAR(64),
    amount              VARCHAR(32),
    currency            VARCHAR(8),

    order_completed     BOOLEAN NOT NULL DEFAULT FALSE,
    inventory_released  BOOLEAN NOT NULL DEFAULT FALSE,
    order_canceled      BOOLEAN NOT NULL DEFAULT FALSE,

    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkout_saga_saga_id
ON checkout_saga (saga_id);

CREATE INDEX IF NOT EXISTS idx_checkout_saga_status_step
ON checkout_saga (status, step);

CREATE INDEX IF NOT EXISTS idx_checkout_saga_updated_at
ON checkout_saga (updated_at);


CREATE TABLE IF NOT EXISTS checkout_saga (
order_id            VARCHAR(64) PRIMARY KEY,
saga_id             VARCHAR(64)  NOT NULL,
correlation_id      VARCHAR(64)  NOT NULL,

    status              VARCHAR(32)  NOT NULL,
    step                VARCHAR(64)  NOT NULL,

    customer_id         VARCHAR(64),
    amount              VARCHAR(32),
    currency            VARCHAR(8),

    order_completed     BOOLEAN NOT NULL DEFAULT FALSE,
    inventory_released  BOOLEAN NOT NULL DEFAULT FALSE,
    order_canceled      BOOLEAN NOT NULL DEFAULT FALSE,

    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkout_saga_saga_id
ON checkout_saga (saga_id);

CREATE INDEX IF NOT EXISTS idx_checkout_saga_status_step
ON checkout_saga (status, step);

CREATE INDEX IF NOT EXISTS idx_checkout_saga_updated_at
ON checkout_saga (updated_at);



V2__create_processed_events.sql

CREATE TABLE IF NOT EXISTS processed_events (
event_id        VARCHAR(128) PRIMARY KEY,
event_type      VARCHAR(128) NOT NULL,
order_id        VARCHAR(64)  NOT NULL,
processed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_order
ON processed_events (order_id);

CREATE INDEX IF NOT EXISTS idx_processed_events_type_time
ON processed_events (event_type, processed_at);


V3__create_outbox_messages.sql

CREATE TABLE IF NOT EXISTS outbox_messages (
id              BIGSERIAL PRIMARY KEY,

    event_id         VARCHAR(128) NOT NULL,
    aggregate_type   VARCHAR(64)  NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,

    event_type       VARCHAR(128) NOT NULL,
    topic            VARCHAR(128) NOT NULL,

    payload_json     TEXT NOT NULL,
    headers_json     TEXT NOT NULL,

    status           VARCHAR(32) NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    published_at     TIMESTAMPTZ,

    last_error       VARCHAR(512),
    retry_count      INTEGER NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL,

    version          BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_id
ON outbox_messages (event_id);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
ON outbox_messages (status, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt
ON outbox_messages (status, next_attempt_at);


application.yaml (flyway ligado)

spring:
flyway:
enabled: true
baseline-on-migrate: true
locations: classpath:db/migration



2) Lista exata de listeners/consumers por microserviço (MVP)

A regra do MVP é:

Orquestrador consome EVENTS

Microserviços consomem COMMANDS

Cada microserviço executa e publica EVENT de resultado no seu tópico de events.

Tópicos (padrão)
Events

order.events.v1

inventory.events.v1

payment.events.v1

Commands

order.commands.v1

inventory.commands.v1

payment.commands.v1

A) ms-checkout-orchestrator (consumidor de events)

KafkaListener (1 consumer group):

Consome:

order.events.v1

inventory.events.v1

payment.events.v1

Event types que ele espera (MVP):

Do ms-order:

order.placed

order.completed

order.canceled

Do ms-inventory:

inventory.reserved

inventory.rejected

inventory.released

Do ms-payment:

payment.authorized

payment.declined

Command types que ele publica (via outbox):

Para ms-inventory em inventory.commands.v1:

inventory.reserve

inventory.release

Para ms-payment em payment.commands.v1:

payment.authorize

Para ms-order em order.commands.v1:

order.complete

order.cancel

B) ms-order (consumidor de commands)

KafkaListener (1 consumer group do ms-order):

Consome:

order.commands.v1

Command types que ele deve suportar (MVP):

order.complete

order.cancel

Events que ele publica (já no seu padrão com outbox):

em order.events.v1

order.placed (você já tem quando dá place())

order.confirmed (opcional no saga MVP; pode existir)

order.completed

order.canceled

Observação importante (MVP pragmático):

O “start” do saga pode ser order.placed (como desenhamos).

order.confirmed pode ficar fora do saga por enquanto (ou virar um passo depois). Se incluir, a saga fica maior.

C) ms-inventory (consumidor de commands)

KafkaListener (1 consumer group do ms-inventory):

Consome:

inventory.commands.v1

Command types que ele deve suportar (MVP):

inventory.reserve

inventory.release

Events que ele publica:

em inventory.events.v1

inventory.reserved (se reservou tudo)

inventory.rejected (se faltou estoque / erro de reserva)

inventory.released (compensação OK)

D) ms-payment (consumidor de commands)

KafkaListener (1 consumer group do ms-payment):

Consome:

payment.commands.v1

Command types que ele deve suportar (MVP):

payment.authorize

Events que ele publica:

em payment.events.v1

payment.authorized

payment.declined

No MVP, você pode simular Stripe: se amount < X aprova, senão recusa.

3) Regras mínimas de idempotência (pra não enlouquecer)
   No orquestrador

Tabela processed_events(event_id) impede reprocessar.

Key recomendada dos events: orderId.

Correlation: x-correlation-id fixo por saga (se não vier, cria no order.placed).

Nos microserviços (order/inventory/payment)

Também vale ter processed_commands(command_id) (opcional no MVP).

Pelo menos garantir que handlers são idempotentes:

order.complete se já completed → ignore

inventory.release se já released → ignore

payment.authorize se já authorized/declined → ignore

4) Extra rápido: nomes de consumer groups (recomendado)

ms-checkout-orchestrator

ms-order

ms-inventory

ms-payment



Se você quiser, no próximo passo eu te mando:

o “contrato final” (lista exata dos DTOs V1 de events e commands) com campos mínimos + exemplos JSON

a tabela de transições da saga (estado → evento → comando → próximo estado)

e um checklist pra você validar tudo no Redpanda Console (tópicos, headers, keys, offsets, retries).


Se você quiser, no próximo passo eu te mando:

o “contrato final” (lista exata dos DTOs V1 de events e commands) com campos mínimos + exemplos JSON

a tabela de transições da saga (estado → evento → comando → próximo estado)

e um checklist pra você validar tudo no Redpanda Console (tópicos, headers, keys, offsets, retries).


Fechado ✅ Vou te mandar (1) o contrato final V1 (events + commands) com exemplos JSON, (2) a tabela da máquina de estados do checkout_saga, e (3) um checklist bem prático pra validar tudo no Redpanda Console + CLI.

Premissa:

ms-checkout-orchestrator consome events e publica commands (via outbox).

ms-order, ms-inventory, ms-payment consomem commands e publicam events.

Key Kafka sempre = orderId.

Headers padrão via SagaHeaders (x-event-id, x-event-type, x-correlation-id, x-causation-id, x-topic-version, etc).

1) Contrato V1 — Events e Commands
   1.1 Headers padrão (todos os tópicos)

Esses headers vão em todas as mensagens (events e commands):

content-type: application/json

x-schema-version: "1" (ou "v1", mas seja consistente; recomendo "1" aqui e x-topic-version="v1")

x-topic-version: "v1"

x-producer: "ms-order" | "ms-checkout-orchestrator" | ...

x-event-id / x-command-id: UUID

x-event-type / x-command-type: string tipo order.placed / inventory.reserve

x-occurred-at: ISO-8601

x-correlation-id: id fixo da saga (uma “raiz”)

x-causation-id: id do evento/comando que causou esta mensagem

x-aggregate-type: "Order" (em tudo que for do pedido)

x-aggregate-id: orderId

traceparent (opcional, se você já usa tracing W3C)

x-saga-id, x-saga-name, x-saga-step (opcional mas recomendado; seu SagaHeaders já está preparado)

Dica: correlation-id = “id da saga”; causation-id = “id do último message que me levou aqui”.

1.2 EVENTS (tópicos *.events.v1)
A) order.events.v1
Event: order.placed

Payload (JSON)

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:19:36.897Z",
"orderId": "ORD-123",
"customerId": "CUST-222",
"items": [
{ "productId": "PROD-100", "quantity": 2, "unitPrice": "59.90" },
{ "productId": "PROD-200", "quantity": 1, "unitPrice": "129.90" }
],
"discount": "0.00",
"total": "249.70",
"currency": "BRL"
}



Importante: o orquestrador precisa de items (pra reservar estoque) e total/currency (pra autorizar pagamento).
Se hoje seu order.placed ainda não tem total/currency, vale ajustar o DTO agora (porque isso simplifica MUITO a vida do orquestrador).

Event: order.completed


{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:20:10.000Z",
"orderId": "ORD-123",
"customerId": "CUST-222"
}



Event: order.canceled

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:20:11.000Z",
"orderId": "ORD-123",
"customerId": "CUST-222",
"reason": "PAYMENT_DECLINED"
}


B) inventory.events.v1
Event: inventory.reserved

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:19:40.000Z",
"orderId": "ORD-123",
"reservationId": "RSV-888",
"items": [
{ "productId": "PROD-100", "quantity": 2 },
{ "productId": "PROD-200", "quantity": 1 }
]
}


Event: inventory.rejected

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:19:40.000Z",
"orderId": "ORD-123",
"reason": "OUT_OF_STOCK",
"details": [
{ "productId": "PROD-200", "requested": 1, "available": 0 }
]
}


Event: inventory.released

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:20:05.000Z",
"orderId": "ORD-123",
"reservationId": "RSV-888"
}


C) payment.events.v1
Event: payment.authorized

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:19:55.000Z",
"orderId": "ORD-123",
"paymentId": "PAY-555",
"amount": "249.70",
"currency": "BRL"
}


Event: payment.declined

{
"eventId": "uuid",
"occurredAt": "2025-12-22T07:19:55.000Z",
"orderId": "ORD-123",
"reason": "INSUFFICIENT_FUNDS"
}


1.3 COMMANDS (tópicos *.commands.v1)
A) inventory.commands.v1
Command: inventory.reserve

{
"commandId": "uuid",
"occurredAt": "2025-12-22T07:19:37.000Z",
"orderId": "ORD-123",
"items": [
{ "productId": "PROD-100", "quantity": 2 },
{ "productId": "PROD-200", "quantity": 1 }
]
}


Command: inventory.release

{
"commandId": "uuid",
"occurredAt": "2025-12-22T07:20:02.000Z",
"orderId": "ORD-123",
"reservationId": "RSV-888"
}


B) payment.commands.v1
Command: payment.authorize

{
"commandId": "uuid",
"occurredAt": "2025-12-22T07:19:41.000Z",
"orderId": "ORD-123",
"customerId": "CUST-222",
"amount": "249.70",
"currency": "BRL",
"paymentMethod": "TEST_TOKEN"
}


No MVP: paymentMethod pode ser "TEST_TOKEN" fixo (simulado).

C) order.commands.v1
Command: order.complete

{
"commandId": "uuid",
"occurredAt": "2025-12-22T07:19:56.000Z",
"orderId": "ORD-123"
}


Command: order.cancel

{
"commandId": "uuid",
"occurredAt": "2025-12-22T07:20:03.000Z",
"orderId": "ORD-123",
"reason": "INVENTORY_REJECTED"
}


2) Máquina de estados da saga (checkout_saga)
   Estados (coluna status e step)

Sugestão simples e profissional:

RUNNING (step variando)

COMPLETED

CANCELED

Steps possíveis:

STARTED

WAIT_INVENTORY

WAIT_PAYMENT

WAIT_ORDER_COMPLETION

COMPENSATING

DONE

Tabela de transição (bem “empresa”)
Início
Quando chega	Condição	Ação do orquestrador	Próximo step
order.placed	sempre	publish inventory.reserve	WAIT_INVENTORY
Inventory
Quando chega	Condição	Ação	Próximo step
inventory.reserved	sempre	publish payment.authorize	WAIT_PAYMENT
inventory.rejected	sempre	publish order.cancel	DONE + CANCELED
Payment
Quando chega	Condição	Ação	Próximo step
payment.authorized	sempre	publish order.complete	WAIT_ORDER_COMPLETION
payment.declined	se já tinha reserva	publish inventory.release e depois order.cancel	COMPENSATING → DONE + CANCELED
Finalização do pedido
Quando chega	Condição	Ação	Próximo
order.completed	sempre	marcar saga COMPLETED	DONE
order.canceled	sempre	marcar saga CANCELED	DONE

Observação: no MVP você pode simplificar a compensação: quando payment.declined, manda inventory.release e já manda order.cancel (sem esperar o released). Mas o “perfeito” é esperar inventory.released antes de cancelar.

3) Checklist de validação (Redpanda Console + CLI)
   3.1 Tópicos existem e com replication=1 (seu caso é 1 broker)

No seu Kafka (docker), rode:


docker exec -it kafka-1 kafka-topics --bootstrap-server localhost:9092 --list


E depois descreve:

docker exec -it kafka-1 kafka-topics --bootstrap-server localhost:9092 --describe --topic order.events.v1


3.4 Consumer groups andando

Quando você subir ms-checkout-orchestrator, valide:

docker exec -it kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --list


E depois:

docker exec -it kafka-1 kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group ms-checkout-orchestrator










































































