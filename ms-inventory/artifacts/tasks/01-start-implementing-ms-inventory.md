



















Pular para o conteúdo
Histórico de chats



Você disse:
pasted.txt
Documento
ja implementei os microserviços: ms-order e ms-checkout-orchestration  de acordo com o coração dos microserviços a seguir... acredito já é um bom momento para comerçarmos a implementar o ms-inventory....                o que voce acha e sugere.... eu nao tenho a minima ideia de quantas e quais tabelas e campos sao envolvidos em o serviço de inventory.... me ajude....     package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CheckoutSagaRepository;
import com.mvbr.retailstore.checkout.config.SagaProperties;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Component
/**
* Scheduler que varre sagas expiradas e dispara retries ou compensacoes.
* Chamado periodicamente pelo Spring Scheduling.
  */
  public class CheckoutSagaTimeoutScheduler {

  private static final Logger log = Logger.getLogger(CheckoutSagaTimeoutScheduler.class.getName());

  private static final String REASON_INVENTORY_TIMEOUT = "INVENTORY_TIMEOUT";
  private static final String REASON_PAYMENT_TIMEOUT = "PAYMENT_TIMEOUT";
  private static final String REASON_ORDER_TIMEOUT = "ORDER_TIMEOUT";

  private final CheckoutSagaRepository sagaRepository;
  private final CheckoutSagaCommandSender commandSender;
  private final SagaProperties sagaProperties;

  public CheckoutSagaTimeoutScheduler(CheckoutSagaRepository sagaRepository,
  CheckoutSagaCommandSender commandSender,
  SagaProperties sagaProperties) {
  this.sagaRepository = sagaRepository;
  this.commandSender = commandSender;
  this.sagaProperties = sagaProperties;
  }

  /**
    * Ponto de entrada do scheduler: busca sagas vencidas e trata uma a uma.
    * Fluxo: Spring Scheduler -> tick() -> handleTimeout().
      */
      @Scheduled(fixedDelayString = "${saga.timeouts.scanFixedDelayMs:5000}")
      @Transactional
      public void tick() {
      Instant now = Instant.now();
      List<CheckoutSaga> timedOut = sagaRepository.findTimedOut(now);
      if (!timedOut.isEmpty()) {
      log.info("CheckoutSagaTimeoutScheduler tick - due sagas: " + timedOut.size());
      }
      for (CheckoutSaga saga : timedOut) {
      handleTimeout(saga);
      }
      }

  /**
    * Direciona o timeout para a acao correta conforme a etapa atual.
      */
      private void handleTimeout(CheckoutSaga saga) {
      String causationId = Optional.ofNullable(saga.getLastEventId()).orElse(saga.getSagaId());
      switch (saga.getStep()) {
      case WAIT_INVENTORY -> handleInventoryTimeout(saga, causationId);
      case WAIT_PAYMENT -> handlePaymentTimeout(saga, causationId);
      case WAIT_ORDER_COMPLETION -> handleOrderCompletionTimeout(saga, causationId);
      default -> { }
      }
      }

  /**
    * Trata timeout de estoque: retry ou compensacao (cancelar pedido).
      */
      private void handleInventoryTimeout(CheckoutSaga saga, String causationId) {
      int maxRetries = sagaProperties.getRetries().getInventoryMax();
      if (saga.getAttemptsInventory() < maxRetries) {
      saga.scheduleInventoryRetry(deadlineAfterSeconds(sagaProperties.getTimeouts().getInventorySeconds()));
      sagaRepository.save(saga);
      commandSender.sendInventoryReserve(saga, causationId, SagaStep.WAIT_INVENTORY.name());
      return;
      }

      saga.onInventoryRejected(REASON_INVENTORY_TIMEOUT);
      sagaRepository.save(saga);
      commandSender.sendOrderCancel(saga, causationId, SagaStep.COMPENSATING.name(), REASON_INVENTORY_TIMEOUT);
      }

  /**
    * Trata timeout de pagamento: retry ou compensacao (liberar estoque + cancelar).
      */
      private void handlePaymentTimeout(CheckoutSaga saga, String causationId) {
      int maxRetries = sagaProperties.getRetries().getPaymentMax();
      if (saga.getAttemptsPayment() < maxRetries) {
      saga.schedulePaymentRetry(deadlineAfterSeconds(sagaProperties.getTimeouts().getPaymentSeconds()));
      sagaRepository.save(saga);
      commandSender.sendPaymentAuthorize(saga, causationId, SagaStep.WAIT_PAYMENT.name());
      return;
      }

      saga.onPaymentDeclined(REASON_PAYMENT_TIMEOUT);
      sagaRepository.save(saga);
      commandSender.sendInventoryRelease(saga, causationId, SagaStep.COMPENSATING.name());
      commandSender.sendOrderCancel(saga, causationId, SagaStep.COMPENSATING.name(), REASON_PAYMENT_TIMEOUT);
      }

  /**
    * Trata timeout de conclusao do pedido: retry ou cancelamento local da saga.
      */
      private void handleOrderCompletionTimeout(CheckoutSaga saga, String causationId) {
      int maxRetries = sagaProperties.getRetries().getOrderCompleteMax();
      if (saga.getAttemptsOrderCompletion() < maxRetries) {
      saga.scheduleOrderCompletionRetry(deadlineAfterSeconds(
      sagaProperties.getTimeouts().getOrderCompleteSeconds()));
      sagaRepository.save(saga);
      commandSender.sendOrderComplete(saga, causationId, SagaStep.WAIT_ORDER_COMPLETION.name());
      return;
      }

      saga.markOrderCanceled(REASON_ORDER_TIMEOUT);
      sagaRepository.save(saga);
      }

  /**
    * Calcula um novo deadline a partir de agora.
      */
      private Instant deadlineAfterSeconds(long seconds) {
      return Instant.now().plusSeconds(seconds);
      }
      }       ...           package com.mvbr.retailstore.checkout.application.service;

import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSaga;
import com.mvbr.retailstore.checkout.domain.model.CheckoutSagaItem;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCancelCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderCompleteCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.PaymentAuthorizeCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
/**
* Construtor de comandos da saga e ponto de saida para o barramento.
* Chamado pelo CheckoutSagaEngine e pelo CheckoutSagaTimeoutScheduler.
  */
  public class CheckoutSagaCommandSender {

  private static final String SAGA_NAME = "checkout";
  private static final String AGGREGATE_TYPE = "Order";

  private final CommandPublisher commandPublisher;

  public CheckoutSagaCommandSender(CommandPublisher commandPublisher) {
  this.commandPublisher = commandPublisher;
  }

  /**
    * Envia comando para reservar estoque.
    * Fluxo: CheckoutSagaEngine/TimeoutScheduler -> este sender -> CommandPublisher.
      */
      public void sendInventoryReserve(CheckoutSaga saga, String causationId, String sagaStep) {
      String commandId = newCommandId();
      InventoryReserveCommandV1 cmd = new InventoryReserveCommandV1(
      commandId,
      now(),
      saga.getOrderId(),
      toItems(saga.getItems())
      );

      Map<String, String> headers = SagaHeaders.forCommand(
      commandId,
      saga.getSagaId(),
      saga.getCorrelationId(),
      causationId,
      SAGA_NAME,
      sagaStep,
      AGGREGATE_TYPE,
      saga.getOrderId()
      );
      applyCommandType(headers, "inventory.reserve");

      commandPublisher.publish(
      TopicNames.INVENTORY_COMMANDS_V1,
      saga.getOrderId(),
      "inventory.reserve",
      cmd,
      headers
      );
      }

  /**
    * Envia comando para autorizar pagamento.
    * Chamado apos inventory.reserved ou por retry de timeout.
      */
      public void sendPaymentAuthorize(CheckoutSaga saga, String causationId, String sagaStep) {
      String commandId = newCommandId();
      PaymentAuthorizeCommandV1 cmd = new PaymentAuthorizeCommandV1(
      commandId,
      now(),
      saga.getOrderId(),
      saga.getCustomerId(),
      saga.getAmount(),
      saga.getCurrency(),
      saga.getPaymentMethod()
      );

      Map<String, String> headers = SagaHeaders.forCommand(
      commandId,
      saga.getSagaId(),
      saga.getCorrelationId(),
      causationId,
      SAGA_NAME,
      sagaStep,
      AGGREGATE_TYPE,
      saga.getOrderId()
      );
      applyCommandType(headers, "payment.authorize");

      commandPublisher.publish(
      TopicNames.PAYMENT_COMMANDS_V1,
      saga.getOrderId(),
      "payment.authorize",
      cmd,
      headers
      );
      }

  /**
    * Envia comando para concluir o pedido no servico de orders.
      */
      public void sendOrderComplete(CheckoutSaga saga, String causationId, String sagaStep) {
      String commandId = newCommandId();
      OrderCompleteCommandV1 cmd = new OrderCompleteCommandV1(
      commandId,
      now(),
      saga.getOrderId()
      );

      Map<String, String> headers = SagaHeaders.forCommand(
      commandId,
      saga.getSagaId(),
      saga.getCorrelationId(),
      causationId,
      SAGA_NAME,
      sagaStep,
      AGGREGATE_TYPE,
      saga.getOrderId()
      );
      applyCommandType(headers, "order.complete");

      commandPublisher.publish(
      TopicNames.ORDER_COMMANDS_V1,
      saga.getOrderId(),
      "order.complete",
      cmd,
      headers
      );
      }

  /**
    * Envia comando para cancelar o pedido (compensacao).
      */
      public void sendOrderCancel(CheckoutSaga saga, String causationId, String sagaStep, String reason) {
      String commandId = newCommandId();
      OrderCancelCommandV1 cmd = new OrderCancelCommandV1(
      commandId,
      now(),
      saga.getOrderId(),
      reason
      );

      Map<String, String> headers = SagaHeaders.forCommand(
      commandId,
      saga.getSagaId(),
      saga.getCorrelationId(),
      causationId,
      SAGA_NAME,
      sagaStep,
      AGGREGATE_TYPE,
      saga.getOrderId()
      );
      applyCommandType(headers, "order.cancel");

      commandPublisher.publish(
      TopicNames.ORDER_COMMANDS_V1,
      saga.getOrderId(),
      "order.cancel",
      cmd,
      headers
      );
      }

  /**
    * Envia comando para liberar estoque (compensacao).
      */
      public void sendInventoryRelease(CheckoutSaga saga, String causationId, String sagaStep) {
      String commandId = newCommandId();
      InventoryReleaseCommandV1 cmd = new InventoryReleaseCommandV1(
      commandId,
      now(),
      saga.getOrderId(),
      null
      );

      Map<String, String> headers = SagaHeaders.forCommand(
      commandId,
      saga.getSagaId(),
      saga.getCorrelationId(),
      causationId,
      SAGA_NAME,
      sagaStep,
      AGGREGATE_TYPE,
      saga.getOrderId()
      );
      applyCommandType(headers, "inventory.release");

      commandPublisher.publish(
      TopicNames.INVENTORY_COMMANDS_V1,
      saga.getOrderId(),
      "inventory.release",
      cmd,
      headers
      );
      }

  /**
    * Converte itens do dominio para o formato do comando de estoque.
      */
      private List<InventoryReserveCommandV1.Item> toItems(List<CheckoutSagaItem> items) {
      if (items == null || items.isEmpty()) {
      return List.of();
      }
      return items.stream()
      .map(item -> new InventoryReserveCommandV1.Item(item.productId(), item.quantity()))
      .toList();
      }

  /**
    * Gera um novo id para comando/evento.
      */
      private String newCommandId() {
      return UUID.randomUUID().toString();
      }

  /**
    * Timestamp padrao para eventos de comando.
      */
      private String now() {
      return Instant.now().toString();
      }

  /**
    * Ajusta headers de tipo de comando e evento para roteamento.
      */
      private void applyCommandType(Map<String, String> headers, String commandType) {
      headers.put(HeaderNames.COMMAND_TYPE, commandType);
      headers.put(HeaderNames.EVENT_TYPE, commandType);
      }
      }
      O ChatGPT disse:

Pensou por 17s
Sim — agora é o momento perfeito pra começar o ms-inventory.

Você já tem o “coração” da orquestração rodando (engine + scheduler + command sender), e ele já define claramente o contrato que o Inventory precisa cumprir:

receber inventory.reserve

responder com inventory.reserved ou inventory.rejected

receber inventory.release (compensação)

responder com inventory.released

Isso está explícito no teu CheckoutSagaEngine pelos eventType que ele trata (ex.: "inventory.reserved", "inventory.rejected", "inventory.released")
pasted


A partir disso, dá pra desenhar um Inventory bem “blindado”, com poucas tabelas, mas cobrindo o que importa em produção: consistência, idempotência, concorrência e compensação.

1) O que o ms-inventory é (no teu projeto)
   Pensa nele como o serviço que mantém:

Saldo de estoque por produto (quanto existe)

Reservas por pedido (quanto está “segurado” pra um orderId enquanto o pagamento acontece)

Liberação/expiração de reserva (compensação e timeout)

No teu fluxo de saga, a reserva é uma “hold” temporária:

Order foi criado → Checkout manda inventory.reserve(orderId, items)

Inventory tenta reservar:

se ok → emite inventory.reserved(orderId, …)

se não → emite inventory.rejected(orderId, reason)

Se pagamento falhar/timeout → Checkout manda inventory.release(orderId)

Inventory desfaz a reserva e emite inventory.released(orderId)

2) Modelo mínimo de tabelas (MVP “de empresa”)
   Tabela A — inventory_items (o saldo por produto)
   Guarda o “estado atual” do estoque.

Campos essenciais:

product_id (PK) — string/uuid

on_hand — estoque físico (total disponível no armazém)

reserved — quanto está reservado (hold) por pedidos em andamento

version — para optimistic locking (ou use lock pessimista)

updated_at

Por que separar on_hand e reserved?
Porque “disponível de verdade” vira: available = on_hand - reserved
Isso evita oversell e facilita auditoria.

Tabela B — inventory_reservations (uma reserva por orderId)
Representa a reserva “daquele pedido”.

Campos essenciais:

reservation_id (PK) — uuid

order_id (UK) — único por pedido (muito importante)

status — PENDING | RESERVED | REJECTED | RELEASED | EXPIRED

reason — motivo quando rejeita (ex.: INSUFFICIENT_STOCK)

created_at

expires_at — pra expirar automaticamente

last_command_id — idempotência/observabilidade (opcional)

correlation_id — se você quiser rastrear saga (opcional)

Chave única em order_id garante:

reprocessamento do mesmo comando não cria duas reservas

Tabela C — inventory_reservation_items (itens da reserva)
Itens reservados por produto.

Campos:

reservation_item_id (PK)

reservation_id (FK)

product_id

quantity

Índice recomendado:

(reservation_id)

(product_id) se precisar consultar rápido

Tabela D — processed_messages (idempotência de consumo)
Você já está usando isso na saga (ProcessedEventRepository). O Inventory precisa do mesmo padrão pra não reservar duas vezes se o Kafka redeliver.

Campos típicos:

message_id (PK) — commandId/eventId

message_type (ex.: inventory.reserve)

aggregate_id (orderId)

processed_at

Comportamento:

markProcessedIfFirst(message_id, type, aggregate) → se já existe, ignora com segurança.

(Recomendado) Tabela E — outbox_messages (pra publicar eventos com consistência)
Se você está “na vibe” de outbox nos outros serviços, usa aqui também:

quando reservar/liberar no DB, grava na outbox

um relay publica no Kafka e marca como enviado

Isso deixa o Inventory “tanque de guerra”.

3) DDL sugerido (Postgres) — simples e forte
   create table inventory_items (
   product_id        varchar(64) primary key,
   on_hand           bigint not null check (on_hand >= 0),
   reserved          bigint not null check (reserved >= 0),
   version           bigint not null,
   updated_at        timestamptz not null
   );

create table inventory_reservations (
reservation_id    uuid primary key,
order_id          varchar(64) not null unique,
status            varchar(16) not null,
reason            varchar(128),
created_at        timestamptz not null,
expires_at        timestamptz not null,
last_command_id   varchar(64),
correlation_id    varchar(64)
);

create index idx_inventory_reservations_status_expires
on inventory_reservations(status, expires_at);

create table inventory_reservation_items (
reservation_item_id bigserial primary key,
reservation_id      uuid not null references inventory_reservations(reservation_id),
product_id          varchar(64) not null,
quantity            bigint not null check (quantity > 0)
);

create index idx_res_items_reservation_id
on inventory_reservation_items(reservation_id);

create table processed_messages (
message_id     varchar(64) primary key,
message_type   varchar(64) not null,
aggregate_id   varchar(64) not null,
processed_at   timestamptz not null
);

create index idx_processed_aggregate_type
on processed_messages(aggregate_id, message_type);
4) Regras de negócio “blindadas” do Inventory
   Regra 1 — Reserva é atômica (tudo ou nada)
   Para inventory.reserve(orderId, items):

você não quer reservar metade e rejeitar metade (a saga fica ambígua)

então:

valida todos os itens (available >= qty pra cada produto)

se ok, aplica todas as reservas

grava inventory_reserved

se falhar qualquer item → rejeita tudo com reason detalhada

Regra 2 — Idempotência total
Se o mesmo commandId chegar 2x, não muda nada.

Se o mesmo orderId já estiver RESERVED, responda inventory.reserved de novo (ou ignore) — mas eu prefiro responder novamente porque ajuda a saga a destravar.

Regra 3 — Concorrência sem oversell
Duas estratégias (escolhe 1):

Opção A (mais simples e forte): SELECT FOR UPDATE

trava as linhas de inventory_items dos produtos envolvidos

calcula disponibilidade e atualiza

é bem confiável em Postgres

Opção B (optimistic lock com version)

lê inventory_items

tenta update ... where version = ?

se falhar, retry poucas vezes

fica “mais elegante”, mas é mais chato de acertar

Pra teu projeto, eu iria de SELECT FOR UPDATE no MVP.

Regra 4 — Expiração de reserva
Você já tem scheduler de timeout na saga. Mesmo assim, Inventory também deve ter “auto-heal”:

Scheduler do Inventory varre reservas RESERVED com expires_at < now()

marca EXPIRED

devolve reserved nos produtos

emite inventory.released (ou inventory.expired, mas como tua saga entende inventory.released, mantém released)

Isso evita estoque “preso” se o checkout morrer.

5) Eventos/Comandos (contratos) que eu sugiro
   Você já tem:

InventoryReserveCommandV1(commandId, occurredAt, orderId, items[])

InventoryReleaseCommandV1(commandId, occurredAt, orderId, reason?) (no teu sender está null)

Eventos recomendados:

inventory.reserved
eventId

occurredAt

orderId

items[] (opcional, mas útil pra auditoria)

expiresAt (muito útil)

inventory.rejected
eventId

occurredAt

orderId

reason (ex.: INSUFFICIENT_STOCK:productId=...)

inventory.released
eventId

occurredAt

orderId

reason (ex.: PAYMENT_DECLINED, INVENTORY_TIMEOUT, EXPIRED)

Isso encaixa perfeitamente com o que teu CheckoutSagaEngine espera tratar por eventType
pasted


6) Fluxos internos do ms-inventory (bem pé-no-chão)
   Consumer: InventoryCommandsConsumer
   Escuta TopicNames.INVENTORY_COMMANDS_V1 e roteia:

inventory.reserve → InventoryApplicationService.reserve(cmd, headers)

inventory.release → InventoryApplicationService.release(cmd, headers)

Application Service: InventoryReservationService
reserve(orderId, items, commandId, correlationId, …)

idempotência: processed_messages

carrega/cria inventory_reservations por orderId

se já RESERVED → republish inventory.reserved (ou no-op)

lock estoque dos produtos

valida disponibilidade

atualiza reserved += qty

salva reservation + items

outbox: inventory.reserved

release(orderId, commandId, …)

idempotência

busca reservation por orderId

se status já RELEASED/EXPIRED/REJECTED → ok idempotente

lock itens de estoque

atualiza reserved -= qty

marca reservation RELEASED

outbox: inventory.released

7) O que eu faria como “primeiro corte” pra você não travar
   Implementa o Inventory em 3 etapas, sem inventar moda:

Etapa 1 — “happy path”
cria as 3 tabelas (items, reservations, reservation_items) + processed_messages

implementa inventory.reserve:

se tudo ok → inventory.reserved

se falta estoque → inventory.rejected

Etapa 2 — compensação
implementa inventory.release → inventory.released

Etapa 3 — robustez
adiciona expires_at + scheduler de expiração

adiciona outbox (se ainda não tiver)

8) Dica valiosa pro teu objetivo de “salário alto”
   Isso aqui, do jeito que você está montando (saga orquestrada + idempotência + outbox + timeouts + compensação), é portfólio de nível sênior.

O “pulo do gato” pra ficar irresistível pra vaga gringa:

README com diagramas de fluxo (saga steps + retries + compensações)

testes de integração com Testcontainers (Postgres + Kafka)

prova de idempotência (mesma mensagem 2x não duplica reserva)

prova de concorrência (2 pedidos competindo pelo mesmo SKU)

Se você fizer isso, vira história de entrevista fácil: “I built an orchestration saga with reliable inventory reservation and compensations, guaranteeing idempotency and consistency with outbox.”

Se você quiser, eu já te devolvo um desenho completo do ms-inventory no teu padrão (ports/adapters), com:

packages

entidades de domínio (Reservation, Stock)

repositórios

consumers/producers Kafka

e os métodos reserve() e release() com transação e lock

Mas mesmo sem isso, só com as tabelas e regras acima você já destrava 100% o design.


Fontes
Você disse:
voce ja conhece como é a minha organização de pastas?  se quiser faço um tree e lhe envio
O ChatGPT disse:
Ainda não conheço com certeza a tua organização de pastas completa (tipo um “mapa” do projeto), porque aqui eu só vi trechos de classes e packages — dá pra inferir que você está seguindo um estilo bem hexagonal/ports-adapters, mas eu não quero inventar estrutura e te fazer refatorar depois.

Então: sim, manda um tree 🙌
E pra eu já conseguir te devolver o ms-inventory “plugável” no teu padrão, manda assim (bem objetivo):

tree do root (só 2 níveis)

tree -L 2
tree só do ms-checkout-orchestration (4 níveis, pra eu ver ports/adapters)

tree -L 4 ms-checkout-orchestration
(opcional, mas ajuda muito) tree do ms-order (3–4 níveis)

tree -L 4 ms-order
Com isso eu vou conseguir:

copiar teu estilo de application/port/in/out, infrastructure/adapter/in/out, domain/model, config, etc.

criar a estrutura do ms-inventory batendo com a tua convenção

sugerir nomes de packages e classes consistentes (consumer, publisher, outbox relay, scheduler de expiração)

Manda o tree aqui na conversa que eu já respondo com a estrutura exata do ms-inventory no teu padrão.

Você disse:
com
└── mvbr
└── retailstore
└── order
├── application
│   ├── command
│   │   ├── PlaceOrderCommand.java
│   │   └── PlaceOrderItemCommand.java
│   ├── mapper
│   ├── port
│   │   ├── in
│   │   │   ├── CancelOrderUseCase.java
│   │   │   ├── ConfirmOrderUseCase.java
│   │   │   └── PlaceOrderUseCase.java
│   │   └── out
│   │       ├── EventPublisher.java
│   │       ├── OrderIdGenerator.java
│   │       └── OrderRepository.java
│   ├── query
│   │   ├── GetOrderItemQuery.java
│   │   ├── GetOrderQuery.java
│   │   ├── ListOrdersQuery.java
│   │   ├── ListOrderSummariesQuery.java
│   │   ├── OrderItemReadModel.java
│   │   ├── OrderQueryFilters.java
│   │   ├── OrderReadModel.java
│   │   ├── OrderReadRepository.java
│   │   └── OrderSummaryReadModel.java
│   ├── service
│   │   └── OrderCommandService.java
│   └── usecase
├── config
│   ├── KafkaConfig.java
│   ├── ObservabilityConfig.java
│   ├── PersistenceConfig.java
│   └── UseCaseConfig.java
├── domain
│   ├── event
│   │   ├── DomainEvent.java
│   │   ├── EventTypes.java
│   │   ├── OrderCanceledEvent.java
│   │   ├── OrderCompletedEvent.java
│   │   ├── OrderConfirmedEvent.java
│   │   └── OrderPlacedEvent.java
│   ├── exception
│   │   ├── DomainException.java
│   │   └── InvalidOrderException.java
│   └── model
│       ├── CustomerId.java
│       ├── Money.java
│       ├── OrderId.java
│       ├── OrderItem.java
│       ├── Order.java
│       ├── OrderStatus.java
│       └── ProductId.java
├── infrastructure
│   └── adapter
│       ├── in
│       │   ├── messaging
│       │   │   └── OrderConsumer.java
│       │   └── web
│       │       ├── ApiExceptionHandler.java
│       │       ├── controller
│       │       │   ├── OrderCommandController.java
│       │       │   └── OrderQueryController.java
│       │       ├── dto
│       │       │   ├── ApiError.java
│       │       │   ├── CreateOrderItemRequest.java
│       │       │   ├── CreateOrderRequest.java
│       │       │   ├── OrderCreatedResponse.java
│       │       │   ├── OrderItemResponse.java
│       │       │   ├── OrderResponse.java
│       │       │   └── OrderSummaryResponse.java
│       │       └── mapper
│       │           └── OrderWebMapper.java
│       └── out
│           ├── idgenerator
│           │   └── UuidOrderIdGenerator.java
│           ├── messaging
│           │   ├── dto
│           │   │   ├── OrderCanceledEventV1.java
│           │   │   ├── OrderCompletedEventV1.java
│           │   │   ├── OrderConfirmedEventV1.java
│           │   │   └── OrderPlacedEventV1.java
│           │   ├── headers
│           │   │   ├── HeaderNames.java
│           │   │   └── SagaHeaders.java
│           │   ├── mapper
│           │   │   ├── OrderCanceledEventMapper.java
│           │   │   ├── OrderCompletedEventMapper.java
│           │   │   ├── OrderConfirmedEventMapper.java
│           │   │   └── OrderPlacedEventMapper.java
│           │   └── outbox
│           │       ├── OutboxEventPublisherAdapter.java
│           │       ├── OutboxJpaRepository.java
│           │       ├── OutboxMessageJpaEntity.java
│           │       ├── OutboxRelay.java
│           │       └── OutboxRetentionJob.java
│           ├── persistence
│           │   ├── JpaOrderEntity.java
│           │   ├── JpaOrderItemEntity.java
│           │   ├── JpaOrderRepositoryAdapter.java
│           │   ├── JpaOrderSpringDataRepository.java
│           │   └── OrderPersistenceMapper.java
│           └── query
│               └── JdbcOrderReadRepository.java
└── OrderApplication.java             ....    ATENÇÃO: trocar o nome da package raiz de acordo com o o dominio novo:  com.mvbr.retailstore.inventory
O ChatGPT disse:

Pensou por 7s
Perfeito — agora eu conheço teu padrão: é bem “hexagonal clean” e bem consistente (application/port/in-out + domain + infrastructure/adapters + config + outbox). Então o ms-inventory pode nascer espelhando o ms-order quase 1:1, só trocando o domínio e os contratos (commands/events).

Abaixo vai uma sugestão bem fiel ao teu estilo, já com a package raiz que você pediu:

com.mvbr.retailstore.inventory

Estrutura sugerida do ms-inventory (no teu padrão)
com
└── mvbr
└── retailstore
└── inventory
├── application
│   ├── command
│   │   ├── ReserveInventoryCommand.java
│   │   ├── ReserveInventoryItemCommand.java
│   │   └── ReleaseInventoryCommand.java
│   ├── mapper
│   │   ├── InventoryCommandMapper.java
│   │   └── InventoryEventMapper.java
│   ├── port
│   │   ├── in
│   │   │   ├── ReserveInventoryUseCase.java
│   │   │   └── ReleaseInventoryUseCase.java
│   │   └── out
│   │       ├── InventoryRepository.java
│   │       ├── ReservationRepository.java
│   │       ├── EventPublisher.java
│   │       ├── ProcessedMessageRepository.java
│   │       └── ClockProvider.java
│   ├── service
│   │   └── InventoryCommandService.java
│   ├── usecase
│   │   ├── ReserveInventoryUseCaseImpl.java
│   │   └── ReleaseInventoryUseCaseImpl.java
│   └── query
│       ├── GetInventoryItemQuery.java
│       ├── InventoryItemReadModel.java
│       └── InventoryReadRepository.java
│
├── config
│   ├── KafkaConfig.java
│   ├── ObservabilityConfig.java
│   ├── PersistenceConfig.java
│   ├── UseCaseConfig.java
│   └── SchedulingConfig.java
│
├── domain
│   ├── event
│   │   ├── DomainEvent.java
│   │   ├── EventTypes.java
│   │   ├── InventoryReservedEvent.java
│   │   ├── InventoryRejectedEvent.java
│   │   └── InventoryReleasedEvent.java
│   ├── exception
│   │   ├── DomainException.java
│   │   ├── InsufficientStockException.java
│   │   └── ReservationNotFoundException.java
│   └── model
│       ├── ProductId.java
│       ├── OrderId.java
│       ├── Quantity.java
│       ├── InventoryItem.java
│       ├── Reservation.java
│       ├── ReservationItem.java
│       └── ReservationStatus.java
│
├── infrastructure
│   └── adapter
│       ├── in
│       │   ├── messaging
│       │   │   ├── InventoryCommandConsumer.java
│       │   │   └── ProcessedMessageGuard.java
│       │   └── web
│       │       ├── ApiExceptionHandler.java
│       │       ├── controller
│       │       │   └── InventoryQueryController.java
│       │       ├── dto
│       │       │   └── InventoryItemResponse.java
│       │       └── mapper
│       │           └── InventoryWebMapper.java
│       │
│       └── out
│           ├── messaging
│           │   ├── TopicNames.java
│           │   ├── dto
│           │   │   ├── InventoryReserveCommandV1.java
│           │   │   ├── InventoryReleaseCommandV1.java
│           │   │   ├── InventoryReservedEventV1.java
│           │   │   ├── InventoryRejectedEventV1.java
│           │   │   └── InventoryReleasedEventV1.java
│           │   ├── headers
│           │   │   ├── HeaderNames.java
│           │   │   └── SagaHeaders.java
│           │   └── mapper
│           │       ├── InventoryReservedEventMapper.java
│           │       ├── InventoryRejectedEventMapper.java
│           │       └── InventoryReleasedEventMapper.java
│           │
│           ├── outbox
│           │   ├── OutboxEventPublisherAdapter.java
│           │   ├── OutboxJpaRepository.java
│           │   ├── OutboxMessageJpaEntity.java
│           │   ├── OutboxRelay.java
│           │   └── OutboxRetentionJob.java
│           │
│           ├── persistence
│           │   ├── JpaInventoryItemEntity.java
│           │   ├── JpaReservationEntity.java
│           │   ├── JpaReservationItemEntity.java
│           │   ├── JpaInventorySpringDataRepository.java
│           │   ├── JpaReservationSpringDataRepository.java
│           │   ├── JpaInventoryRepositoryAdapter.java
│           │   └── InventoryPersistenceMapper.java
│           │
│           └── scheduling
│               └── ReservationExpirationJob.java
│
└── InventoryApplication.java
Por que isso encaixa 100% no teu jeito?
Você já usa Outbox + Relay + RetentionJob no ms-order → repete no Inventory (fica consistente e confiável).

Você já separa port in/out e implementações em service/usecase → mesmo esquema.

Você já tem adapter.in.messaging e adapter.out.messaging com dto/headers/mapper → Inventory vai consumir commands e publicar events do mesmo jeito.

O que dá pra cortar no MVP (sem perder “enterprise vibes”)
Se você quiser começar bem rápido, dá pra fazer assim:

✅ mantém:

application.command

application.port.in/out

application.service

domain.model/event/exception

infrastructure.adapter.in.messaging

infrastructure.adapter.out.persistence

infrastructure.adapter.out.outbox

infrastructure.adapter.out.messaging (pra publicar eventos)

⛔ pode remover no MVP:

infrastructure.adapter.in.web (Inventory pode ser 100% Kafka no início)

application.query (só se você fizer endpoints/admin depois)

Como eu mapearia as “tabelas” para os adapters de persistence
No pacote infrastructure/adapter/out/persistence:

JpaInventoryItemEntity → tabela inventory_items (saldo)

JpaReservationEntity → tabela inventory_reservations (uma por orderId)

JpaReservationItemEntity → tabela inventory_reservation_items (itens)

E o idempotency:

ProcessedMessageJpaEntity + repo → tabela processed_messages

E o outbox:

OutboxMessageJpaEntity → outbox_messages (igual ao order)

Contratos Kafka: como fica plugado na tua saga
InventoryCommandConsumer lê TopicNames.INVENTORY_COMMANDS_V1

se commandType = inventory.reserve → ReserveInventoryUseCase

se commandType = inventory.release → ReleaseInventoryUseCase

OutboxEventPublisherAdapter publica em TopicNames.INVENTORY_EVENTS_V1

inventory.reserved

inventory.rejected

inventory.released

Isso “casa” direto com o que seu checkout já está fazendo (os command types que você envia já estão bem definidos no CheckoutSagaCommandSender).

Próximo passo (sem enrolação)
Se você topar, eu já te devolvo uma primeira versão “MVP blindada” do inventory com:

os VOs (ProductId, OrderId, Quantity)

o Aggregate Reservation + ReservationStatus

InventoryCommandService.reserve() / release() com:

idempotência (processed_messages)

transação

lock (padrão “for update” ou optimistic)

DTOs Kafka V1 + headers iguais ao teu estilo

ReservationExpirationJob (expiração automática)

Só me diz uma coisa objetiva (sem precisar mandar mais tree):

você quer Inventory em Postgres também (igual ao Order)?
Se sim, eu sigo 100% o mesmo stack e a mesma pegada de JPA + outbox.

Você disse:
Sim, eu quero.... é mesma stack... não faça a parte web
O ChatGPT disse:

Pensou por 27s
Fechado ✅ Mesma stack (Postgres + JPA + Kafka + Outbox) e sem parte web.

Abaixo eu já te passo a primeira versão MVP “blindada” do ms-inventory no mesmo padrão do ms-order, com:

estrutura de pastas (igual ao teu estilo)

tabelas/entidades JPA

ports/usecases/service

consumer Kafka (commands)

outbox + relay

job de expiração (libera reserva travada)

lógica de reserva/liberação idempotente e concorrente

Package raiz: com.mvbr.retailstore.inventory

1) Estrutura de pastas (espelho do teu ms-order, sem web)
   com/mvbr/retailstore/inventory
   ├── InventoryApplication.java
   ├── application
   │   ├── command
   │   │   ├── ReleaseInventoryCommand.java
   │   │   ├── ReserveInventoryCommand.java
   │   │   └── ReserveInventoryItemCommand.java
   │   ├── port
   │   │   ├── in
   │   │   │   ├── ReleaseInventoryUseCase.java
   │   │   │   └── ReserveInventoryUseCase.java
   │   │   └── out
   │   │       ├── EventPublisher.java
   │   │       ├── InventoryItemRepository.java
   │   │       ├── ProcessedMessageRepository.java
   │   │       └── ReservationRepository.java
   │   ├── service
   │   │   ├── InventoryCommandService.java
   │   │   └── ReservationExpirationService.java
   │   └── usecase
   │       ├── ReleaseInventoryUseCaseImpl.java
   │       └── ReserveInventoryUseCaseImpl.java
   ├── config
   │   ├── KafkaConfig.java
   │   ├── ObservabilityConfig.java
   │   ├── PersistenceConfig.java
   │   ├── SchedulingConfig.java
   │   ├── UseCaseConfig.java
   │   └── InventoryProperties.java
   ├── domain
   │   ├── event
   │   │   ├── DomainEvent.java
   │   │   ├── EventTypes.java
   │   │   ├── InventoryRejectedEvent.java
   │   │   ├── InventoryReleasedEvent.java
   │   │   └── InventoryReservedEvent.java
   │   ├── exception
   │   │   ├── DomainException.java
   │   │   ├── InsufficientStockException.java
   │   │   └── ReservationClosedException.java
   │   └── model
   │       ├── OrderId.java
   │       ├── ProductId.java
   │       ├── Quantity.java
   │       ├── InventoryItem.java
   │       ├── Reservation.java
   │       ├── ReservationItem.java
   │       └── ReservationStatus.java
   └── infrastructure
   └── adapter
   ├── in
   │   └── messaging
   │       └── InventoryCommandConsumer.java
   └── out
   ├── messaging
   │   ├── TopicNames.java
   │   ├── dto
   │   │   ├── InventoryReleaseCommandV1.java
   │   │   ├── InventoryReserveCommandV1.java
   │   │   ├── InventoryRejectedEventV1.java
   │   │   ├── InventoryReleasedEventV1.java
   │   │   └── InventoryReservedEventV1.java
   │   ├── headers
   │   │   ├── HeaderNames.java
   │   │   └── SagaHeaders.java
   │   └── outbox
   │       └── OutboxEventPublisherAdapter.java
   ├── outbox
   │   ├── OutboxJpaRepository.java
   │   ├── OutboxMessageJpaEntity.java
   │   ├── OutboxRelay.java
   │   └── OutboxRetentionJob.java
   └── persistence
   ├── JpaInventoryItemEntity.java
   ├── JpaReservationEntity.java
   ├── JpaReservationItemEntity.java
   ├── JpaProcessedMessageEntity.java
   ├── JpaInventorySpringDataRepository.java
   ├── JpaReservationSpringDataRepository.java
   ├── JpaProcessedMessageSpringDataRepository.java
   ├── JpaInventoryRepositoryAdapter.java
   └── InventoryPersistenceMapper.java
2) Tabelas mínimas (Postgres) — “empresa real”, sem excesso
   2.1 inventory_items
   product_id (PK)

on_hand (total físico)

reserved (hold)

version (optimistic lock opcional)

updated_at

2.2 inventory_reservations
reservation_id (PK)

order_id (UNIQUE)

status (PENDING | RESERVED | REJECTED | RELEASED | EXPIRED)

reason

created_at

expires_at

last_command_id

correlation_id

2.3 inventory_reservation_items
id (PK)

reservation_id (FK)

product_id

quantity

2.4 processed_messages
message_id (PK) = commandId

message_type = inventory.reserve / inventory.release

aggregate_id = orderId

processed_at

2.5 outbox_messages
Igual ao ms-order (reaproveita teu padrão).

3) Contratos Kafka (o Inventory “fala” com a tua saga)
   Commands (entrada)
   inventory.reserve (orderId + itens)

inventory.release (orderId + reason opcional)

Events (saída)
inventory.reserved (inclui expiresAt)

inventory.rejected (reason)

inventory.released (reason: PAYMENT_TIMEOUT / PAYMENT_DECLINED / EXPIRED / etc)

Você já está usando HeaderNames.COMMAND_TYPE / EVENT_TYPE no teu ecossistema, então o consumer vai rotear por header (igual ao teu estilo).

4) Ponto principal: lógica “tanque” da reserva/liberação
   4.1 Reserva (inventory.reserve)
   Objetivo: ou reserva tudo, ou rejeita tudo.

Regras que eu cravaria no MVP:

Idempotência por commandId

se o mesmo command chegar de novo, não pode duplicar reserved

mas eu recomendo republicar o resultado (reserved/rejected) pra saga destravar.

Concorrência sem oversell

usa lock pessimista nas linhas de inventory_items (Postgres aguenta bem)

Status por orderId

order_id UNIQUE em inventory_reservations impede duas reservas “do mesmo pedido”

4.2 Liberação (inventory.release)
se já foi liberado/rejeitado/expirado → responde idempotente com inventory.released

se estiver RESERVED → decrementa reserved e marca RELEASED

4.3 Expiração (auto-heal)
Mesmo com teu scheduler no checkout, inventory precisa se curar sozinho:

job varre RESERVED com expires_at < now()

aplica liberação e emite inventory.released com reason EXPIRED

5) Código: “núcleo” que vale ouro (service)
   A ideia aqui é você implementar no InventoryCommandService e deixar usecases só delegando.

5.1 Ports (bem no teu estilo)
// application/port/in
public interface ReserveInventoryUseCase {
void reserve(ReserveInventoryCommand command, SagaContext ctx);
}

public interface ReleaseInventoryUseCase {
void release(ReleaseInventoryCommand command, SagaContext ctx);
}

// application/port/out
public interface ReservationRepository {
Optional<Reservation> findByOrderId(String orderId);
Reservation save(Reservation reservation);
List<Reservation> findExpiredReserved(Instant now, int limit);
}

public interface InventoryItemRepository {
List<InventoryItem> lockByProductIds(List<String> productIds); // SELECT FOR UPDATE
InventoryItem save(InventoryItem item);
}

public interface ProcessedMessageRepository {
boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt);
}

public interface EventPublisher {
void publish(DomainEvent event, Map<String, String> headers);
}
SagaContext pode ser um record simples com sagaId/correlationId/causationId/sagaName/sagaStep (igual seus headers).

5.2 Service: reserva (o “coração”)
Pseudo-código bem direto (pra você implementar sem dor):

@Transactional
public void reserve(ReserveInventoryCommand cmd, SagaContext ctx) {

// 1) idempotência
boolean first = processedRepo.markProcessedIfFirst(cmd.commandId(), "inventory.reserve", cmd.orderId(), now());
if (!first) {
republishOutcome(cmd.orderId(), ctx); // reserved/rejected se já existir
return;
}

// 2) se já existe reserva por orderId, responde idempotente
var existing = reservationRepo.findByOrderId(cmd.orderId());
if (existing.isPresent()) {
republishOutcome(existing.get(), ctx);
return;
}

// 3) cria reserva PENDING
Reservation reservation = Reservation.pending(cmd.orderId(), ttlExpiresAt(), ctx.correlationId(), cmd.commandId());
reservation = reservationRepo.save(reservation);

// 4) lock estoque dos produtos envolvidos
List<String> productIds = cmd.items().stream().map(ReserveInventoryItemCommand::productId).toList();
List<InventoryItem> items = inventoryRepo.lockByProductIds(productIds);

// 5) valida disponibilidade: available = onHand - reserved
for each commandItem:
InventoryItem stock = findOrCreateZero(items, productId)
if stock.available() < qty -> reject

// 6) aplica reserva
for each commandItem:
stock.reserve(qty)
inventoryRepo.save(stock)
reservation.addItem(productId, qty)

reservation.markReserved();
reservationRepo.save(reservation);

// 7) publica evento via Outbox (EventPublisher escreve outbox)
eventPublisher.publish(new InventoryReservedEvent(...expiresAt...), sagaHeaders(ctx, "inventory.reserved", orderId));
}
5.3 Service: liberação (compensação)
@Transactional
public void release(ReleaseInventoryCommand cmd, SagaContext ctx) {

boolean first = processedRepo.markProcessedIfFirst(cmd.commandId(), "inventory.release", cmd.orderId(), now());
if (!first) {
republishReleased(cmd.orderId(), cmd.reason(), ctx);
return;
}

var reservationOpt = reservationRepo.findByOrderId(cmd.orderId());
if (reservationOpt.isEmpty()) {
publishReleased(cmd.orderId(), "NOT_FOUND", ctx); // idempotente e simples
return;
}

Reservation reservation = reservationOpt.get();

if (!reservation.isReserved()) {
publishReleased(cmd.orderId(), "ALREADY_" + reservation.status(), ctx);
return;
}

// lock itens
List<String> productIds = reservation.items().map(ReservationItem::productId).toList();
List<InventoryItem> stocks = inventoryRepo.lockByProductIds(productIds);

for each reservationItem:
stock.release(qty) // reserved -= qty (nunca deixar negativo)
inventoryRepo.save(stock)

reservation.markReleased(cmd.reasonOr("RELEASED"));
reservationRepo.save(reservation);

publishReleased(cmd.orderId(), cmd.reasonOr("RELEASED"), ctx);
}
6) Consumer Kafka (entrada), sem web, roteando por header
   InventoryCommandConsumer:

@KafkaListener(topics = INVENTORY_COMMANDS_V1, groupId = "ms-inventory")

lê header COMMAND_TYPE

desserializa JSON para InventoryReserveCommandV1 / InventoryReleaseCommandV1

monta SagaContext a partir dos headers (SAGA_ID, CORRELATION_ID, CAUSATION_ID, SAGA_STEP, etc)

chama usecase correspondente

7) Outbox (saída) igual ao teu Order
   Você já tem isso no order:

OutboxMessageJpaEntity

OutboxJpaRepository

OutboxRelay

OutboxRetentionJob

Copia o padrão, só muda:

aggregateType = "Order" (igual tua saga)

eventType = inventory.reserved|rejected|released

topic = TopicNames.INVENTORY_EVENTS_V1

8) Job de expiração (sem depender do checkout)
   ReservationExpirationService + ReservationExpirationJob:

a cada X segundos:

findExpiredReserved(now, limit)

para cada reservation:

chama o mesmo fluxo de release internamente, com reason EXPIRED

publica inventory.released

Isso evita estoque preso se o checkout cair.

9) Properties (pra ficar elegante)
   InventoryProperties:

reservationTtlSeconds (default 120)

expirationScanFixedDelayMs (default 5000)

expirationBatchSize (default 50)

E você injeta no service.

10) Próximo passo prático (sem web, direto ao ponto)
    Se você quiser, eu continuo e te entrego a versão “quase copiável” das classes mais críticas (já no teu estilo de naming):

DTOs Kafka V1 (InventoryReserveCommandV1, InventoryReservedEventV1, etc)

InventoryCommandConsumer completo (com header routing)

Entidades JPA (JpaInventoryItemEntity, JpaReservationEntity, JpaProcessedMessageEntity)

Repositórios Spring Data com lock pessimista

OutboxEventPublisherAdapter (port out → grava outbox)

InventoryCommandService completo (reserve/release)

ReservationExpirationJob

Eu não vou fazer web, como você pediu.

Só vou assumir (padrão bom):

TopicNames.INVENTORY_COMMANDS_V1 = "inventory.commands.v1"

TopicNames.INVENTORY_EVENTS_V1 = "inventory.events.v1"

Se você já tem esses nomes definidos no checkout, eu sigo exatamente os teus (sem mudar nada). Se quiser, cola aqui o teu TopicNames do checkout e eu espelho igualzinho.

Você disse:
Em que local acho topic nomes.?
O ChatGPT disse:
No teu padrão, os nomes de tópicos ficam no adapter de messaging.

Pelo trecho que você já mostrou do ms-checkout-orchestration, você está importando:

com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames

Ou seja, no ms-checkout-orchestration o arquivo está (ou deveria estar) aqui:

ms-checkout-orchestration/
src/main/java/
com/mvbr/retailstore/checkout/
infrastructure/adapter/out/messaging/
TopicNames.java
E no ms-order, pelo teu tree, você já tem este caminho para messaging:

com/mvbr/retailstore/order/
infrastructure/adapter/out/messaging/
dto/
headers/
mapper/
outbox/
Então, por consistência, no ms-inventory também seria:

com/mvbr/retailstore/inventory/
infrastructure/adapter/out/messaging/
TopicNames.java
Como achar rapidamente no terminal
Na raiz do repo:

# achar a classe TopicNames
find . -name "TopicNames.java"

# ou achar onde os tópicos são usados
grep -R "INVENTORY_COMMANDS_V1" -n .

# ou procurar por "commands.v1"/"events.v1"
grep -R "commands.v1\|events.v1" -n .
Se você colar aqui o conteúdo do teu TopicNames.java do checkout, eu já te digo quais tópicos o inventory precisa (commands/events) e deixo 100% alinhado com o que você já padronizou.

Você disse:
package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging;

/**
* Constantes com nomes de topicos Kafka usados pela saga.
  */
  public final class TopicNames {

  /**
    * Evita instancia; apenas constantes.
      */
      private TopicNames() {}

  public static final String ORDER_EVENTS_V1 = "order.events.v1";
  public static final String INVENTORY_EVENTS_V1 = "inventory.events.v1";
  public static final String PAYMENT_EVENTS_V1 = "payment.events.v1";

  public static final String ORDER_COMMANDS_V1 = "order.commands.v1";
  public static final String INVENTORY_COMMANDS_V1 = "inventory.commands.v1";
  public static final String PAYMENT_COMMANDS_V1 = "payment.commands.v1";
  }
  O ChatGPT disse:
  Boa — então está cravado: o ms-inventory tem que usar exatamente esses tópicos:

consumir: inventory.commands.v1

publicar: inventory.events.v1

E o checkout já está alinhado com isso.

O que você cria no ms-inventory
1) Classe TopicNames (mesmo padrão, mas no pacote do inventory)
   Arquivo:

com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/TopicNames.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging;

/**
* Constantes com nomes de topicos Kafka usados pelo ms-inventory.
  */
  public final class TopicNames {

  private TopicNames() {}

  public static final String INVENTORY_EVENTS_V1 = "inventory.events.v1";
  public static final String INVENTORY_COMMANDS_V1 = "inventory.commands.v1";
  }
  Repare: eu deixei só os dois que o inventory realmente usa.
  Se você preferir padronizar tudo em todos os serviços, também pode repetir os 6 (order/payment) — mas não é obrigatório.

2) No ms-inventory, onde entra isso?
   Consumer (entrada)
   InventoryCommandConsumer vai ter:

@KafkaListener(topics = TopicNames.INVENTORY_COMMANDS_V1, ...)

Publisher/Outbox Relay (saída)
O OutboxRelay ou publisher vai publicar em:

TopicNames.INVENTORY_EVENTS_V1

3) Mapa “tipos” que vão trafegar nesses tópicos
   Em inventory.commands.v1 (entrada)
   commandType = inventory.reserve

commandType = inventory.release

Em inventory.events.v1 (saída)
eventType = inventory.reserved

eventType = inventory.rejected

eventType = inventory.released

Esses eventType batem com o que a tua saga já trata no checkout.

Se você quiser, eu já escrevo o InventoryCommandConsumer no teu padrão (roteando por HeaderNames.COMMAND_TYPE) e a casca do OutboxEventPublisherAdapter publicando em inventory.events.v1.

Você disse:
Sim
O ChatGPT disse:

Pensou por 21s
Fechado. Vou te entregar o InventoryCommandConsumer completo (roteando por HeaderNames.COMMAND_TYPE) e a casca do OutboxEventPublisherAdapter gravando na outbox (igual teu ms-order), publicando depois via relay pro tópico inventory.events.v1.

Vou assumir o mesmo padrão de headers que você já usa no ecossistema (HeaderNames + SagaHeaders). Onde eu não tiver o valor exato das chaves (strings), eu vou te orientar a copiar 1:1 do ms-order/ms-checkout pra manter compatibilidade.

1) HeaderNames.java (copie igual ao padrão que você já usa)
   📍 Local (ms-inventory):
   com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/headers/HeaderNames.java

Importante: para 100% compatibilidade, a forma mais segura é copiar o arquivo HeaderNames.java do ms-order e só trocar a package.
Abaixo vai um modelo típico (se o teu for igual, pode usar direto).

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.headers;

/**
* Nomes padrao de headers usados entre servicos (commands/events).
* Recomendado: manter IDENTICO ao ms-order/ms-checkout para compatibilidade.
  */
  public final class HeaderNames {

  private HeaderNames() {}

  public static final String COMMAND_TYPE = "command_type";
  public static final String EVENT_TYPE = "event_type";

  public static final String MESSAGE_ID = "message_id";

  public static final String SAGA_ID = "saga_id";
  public static final String CORRELATION_ID = "correlation_id";
  public static final String CAUSATION_ID = "causation_id";
  public static final String SAGA_NAME = "saga_name";
  public static final String SAGA_STEP = "saga_step";

  public static final String AGGREGATE_TYPE = "aggregate_type";
  public static final String AGGREGATE_ID = "aggregate_id";
  }
  Se no teu ms-order os valores forem "COMMAND_TYPE" etc., então ajusta aqui igualzinho (o nome da constante pode ser igual; o que importa é o valor).

2) TopicNames.java (ms-inventory)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/TopicNames.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging;

/**
* Constantes com nomes de topicos Kafka usados pelo ms-inventory.
  */
  public final class TopicNames {

  private TopicNames() {}

  public static final String INVENTORY_EVENTS_V1 = "inventory.events.v1";
  public static final String INVENTORY_COMMANDS_V1 = "inventory.commands.v1";
  }
3) DTOs de comandos Kafka V1 (entrada)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/dto/InventoryReserveCommandV1.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

import java.util.List;

/**
* Comando recebido do checkout: reservar estoque.
  */
  public record InventoryReserveCommandV1(
  String commandId,
  String occurredAt,
  String orderId,
  List<Item> items
  ) {
  public record Item(String productId, long quantity) {}
  }
  📍 .../dto/InventoryReleaseCommandV1.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

/**
* Comando recebido do checkout: liberar reserva de estoque.
  */
  public record InventoryReleaseCommandV1(
  String commandId,
  String occurredAt,
  String orderId,
  String reason
  ) {}
  Observação: no teu sender o reason está indo null hoje. Deixa assim mesmo (nullable). 👍

4) Commands internos da aplicação (application/command)
   📍 com/mvbr/retailstore/inventory/application/command/ReserveInventoryCommand.java

package com.mvbr.retailstore.inventory.application.command;

import java.util.List;

public record ReserveInventoryCommand(
String commandId,
String orderId,
List<ReserveInventoryItemCommand> items
) {}
📍 ReserveInventoryItemCommand.java

package com.mvbr.retailstore.inventory.application.command;

public record ReserveInventoryItemCommand(
String productId,
long quantity
) {}
📍 ReleaseInventoryCommand.java

package com.mvbr.retailstore.inventory.application.command;

public record ReleaseInventoryCommand(
String commandId,
String orderId,
String reason
) {}
5) SagaContext (pra não ficar passando Map solto)
   📍 com/mvbr/retailstore/inventory/application/command/SagaContext.java

package com.mvbr.retailstore.inventory.application.command;

/**
* Contexto de saga carregado a partir dos headers Kafka.
* Mantem rastreabilidade (sagaId, correlationId, causationId, step...).
  */
  public record SagaContext(
  String sagaId,
  String correlationId,
  String causationId,
  String sagaName,
  String sagaStep,
  String aggregateType,
  String aggregateId
  ) {}
6) UseCases (ports in)
   📍 application/port/in/ReserveInventoryUseCase.java

package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

public interface ReserveInventoryUseCase {
void reserve(ReserveInventoryCommand command, SagaContext sagaContext);
}
📍 ReleaseInventoryUseCase.java

package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

public interface ReleaseInventoryUseCase {
void release(ReleaseInventoryCommand command, SagaContext sagaContext);
}
7) ✅ InventoryCommandConsumer (roteamento por COMMAND_TYPE)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/in/messaging/InventoryCommandConsumer.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryItemCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.port.in.ReserveInventoryUseCase;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.TopicNames;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReserveCommandV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.headers.HeaderNames;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers as SpringHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@Component
public class InventoryCommandConsumer {

    private static final Logger log = Logger.getLogger(InventoryCommandConsumer.class.getName());

    private final ObjectMapper objectMapper;
    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final ReleaseInventoryUseCase releaseInventoryUseCase;

    public InventoryCommandConsumer(ObjectMapper objectMapper,
                                   ReserveInventoryUseCase reserveInventoryUseCase,
                                   ReleaseInventoryUseCase releaseInventoryUseCase) {
        this.objectMapper = objectMapper;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.releaseInventoryUseCase = releaseInventoryUseCase;
    }

    @KafkaListener(
            topics = TopicNames.INVENTORY_COMMANDS_V1,
            groupId = "${spring.kafka.consumer.group-id:ms-inventory}"
    )
    public void onMessage(String payload,
                          @SpringHeaders Map<String, Object> headersMap,
                          Headers nativeHeaders) {

        String commandType = headerAsString(headersMap, HeaderNames.COMMAND_TYPE)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.COMMAND_TYPE).orElse(null));

        if (commandType == null || commandType.isBlank()) {
            log.warning("InventoryCommandConsumer: missing command type header. Ignoring message.");
            return;
        }

        SagaContext sagaContext = buildSagaContext(headersMap, nativeHeaders);

        try {
            switch (commandType) {
                case "inventory.reserve" -> handleReserve(payload, sagaContext);
                case "inventory.release" -> handleRelease(payload, sagaContext);
                default -> log.warning("InventoryCommandConsumer: unknown commandType=" + commandType);
            }
        } catch (Exception e) {
            // Aqui você escolhe: logar e rethrow (pra retry do Kafka) ou engolir.
            // Em geral: rethrow pra não perder mensagem.
            log.severe("InventoryCommandConsumer failed. commandType=" + commandType + " error=" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void handleReserve(String payload, SagaContext sagaContext) throws Exception {
        InventoryReserveCommandV1 dto = objectMapper.readValue(payload, InventoryReserveCommandV1.class);

        List<ReserveInventoryItemCommand> items = dto.items() == null ? List.of() :
                dto.items().stream()
                        .map(i -> new ReserveInventoryItemCommand(i.productId(), i.quantity()))
                        .toList();

        ReserveInventoryCommand cmd = new ReserveInventoryCommand(dto.commandId(), dto.orderId(), items);
        reserveInventoryUseCase.reserve(cmd, sagaContext);
    }

    private void handleRelease(String payload, SagaContext sagaContext) throws Exception {
        InventoryReleaseCommandV1 dto = objectMapper.readValue(payload, InventoryReleaseCommandV1.class);

        ReleaseInventoryCommand cmd = new ReleaseInventoryCommand(dto.commandId(), dto.orderId(), dto.reason());
        releaseInventoryUseCase.release(cmd, sagaContext);
    }

    private SagaContext buildSagaContext(Map<String, Object> headersMap, Headers nativeHeaders) {
        // Puxa tanto do map quanto do native headers pra aumentar compatibilidade.
        String sagaId = headerAsString(headersMap, HeaderNames.SAGA_ID)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.SAGA_ID).orElse(null));

        String correlationId = headerAsString(headersMap, HeaderNames.CORRELATION_ID)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.CORRELATION_ID).orElse(null));

        String causationId = headerAsString(headersMap, HeaderNames.CAUSATION_ID)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.CAUSATION_ID).orElse(null));

        String sagaName = headerAsString(headersMap, HeaderNames.SAGA_NAME)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.SAGA_NAME).orElse(null));

        String sagaStep = headerAsString(headersMap, HeaderNames.SAGA_STEP)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.SAGA_STEP).orElse(null));

        String aggregateType = headerAsString(headersMap, HeaderNames.AGGREGATE_TYPE)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.AGGREGATE_TYPE).orElse(null));

        String aggregateId = headerAsString(headersMap, HeaderNames.AGGREGATE_ID)
                .orElseGet(() -> headerFromNative(nativeHeaders, HeaderNames.AGGREGATE_ID).orElse(null));

        return new SagaContext(sagaId, correlationId, causationId, sagaName, sagaStep, aggregateType, aggregateId);
    }

    private Optional<String> headerAsString(Map<String, Object> headers, String name) {
        if (headers == null) return Optional.empty();
        Object v = headers.get(name);
        if (v == null) return Optional.empty();
        if (v instanceof String s) return Optional.of(s);
        if (v instanceof byte[] b) return Optional.of(new String(b, StandardCharsets.UTF_8));
        return Optional.of(v.toString());
    }

    private Optional<String> headerFromNative(Headers headers, String name) {
        if (headers == null) return Optional.empty();
        var h = headers.lastHeader(name);
        if (h == null || h.value() == null) return Optional.empty();
        return Optional.of(new String(h.value(), StandardCharsets.UTF_8));
    }
}
Nota: eu deixei compatível com duas formas de header (map e native), porque dependendo da config do Spring Kafka os headers aparecem diferentes.

8) Port out EventPublisher + ✅ OutboxEventPublisherAdapter (casca)
   8.1 Port out
   📍 com/mvbr/retailstore/inventory/application/port/out/EventPublisher.java

package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.event.DomainEvent;

import java.util.Map;

public interface EventPublisher {
void publish(String topic, String key, String eventType, DomainEvent event, Map<String, String> headers);
}
8.2 Adapter outbox (grava na outbox, não publica direto)
📍 .../infrastructure/adapter/out/messaging/outbox/OutboxEventPublisherAdapter.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.domain.event.DomainEvent;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxMessageJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(String topic, String key, String eventType, DomainEvent event, Map<String, String> headers) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String headersJson = objectMapper.writeValueAsString(headers);

            OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity();
            msg.setEventId(extractEventIdOrRandom(event));
            msg.setAggregateType(headers.getOrDefault("aggregate_type", "Order")); // ideal: usar HeaderNames.AGGREGATE_TYPE
            msg.setAggregateId(headers.getOrDefault("aggregate_id", key));
            msg.setTopic(topic);
            msg.setEventType(eventType);
            msg.setMessageKey(key);
            msg.setPayload(payload);
            msg.setHeaders(headersJson);
            msg.setStatus(OutboxMessageJpaEntity.Status.PENDING);
            msg.setCreatedAt(Instant.now());
            msg.setNextAttemptAt(Instant.now());
            msg.setAttempts(0);

            outboxRepository.save(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize/persist outbox message for eventType=" + eventType, e);
        }
    }

    private String extractEventIdOrRandom(DomainEvent event) {
        // Se teu DomainEvent já tem getEventId(), usa isso.
        // Aqui deixei fallback pra não travar o MVP.
        try {
            var m = event.getClass().getMethod("getEventId");
            Object v = m.invoke(event);
            if (v != null) return v.toString();
        } catch (Exception ignored) { }
        return UUID.randomUUID().toString();
    }
}
Observações importantes (pra bater 100% no teu ms-order)
O OutboxMessageJpaEntity acima eu tratei como “com setters”. No teu ms-order ele já existe e tem campos/nomes específicos.

O certo é você copiar o OutboxMessageJpaEntity + OutboxJpaRepository + OutboxRelay + OutboxRetentionJob do ms-order, trocar package, e só ajustar:

topic → inventory.events.v1

aggregateType → "Order" (porque a saga usa Order como aggregate)

eventType → inventory.reserved|rejected|released

Próximo passo (pra fechar o “ciclo”)
Agora que você tem consumer + publisher pra outbox, o que falta pra “rodar de verdade” é:

Entidades JPA do inventory (InventoryItem, Reservation, etc) + repositories com lock

UseCases + InventoryCommandService com a regra de reserva/liberação idempotente

OutboxRelay publicando no Kafka (você já tem esse padrão no ms-order — é literalmente copiar e trocar topic)

Se você colar aqui teu OutboxMessageJpaEntity do ms-order (só ele), eu te devolvo o OutboxEventPublisherAdapter perfeitamente encaixado com os campos reais dele (sem suposições).

Você disse:
package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
* Entidade JPA para mensagens do padrao outbox (publicacao confiavel).
* Criada pelo OutboxCommandPublisherAdapter e publicada pelo OutboxRelay.
  */
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

  /**
    * Estados do processamento da outbox.
      */
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

  /**
    * Construtor padrao exigido pelo JPA.
      */
      protected OutboxMessageJpaEntity() {}

  /**
    * Cria uma nova mensagem com status PENDING pronta para publicacao.
      */
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

  /*
    * Getters usados pelo OutboxRelay e pelo JPA.
      */
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

  /**
    * Marca a mensagem como em processamento antes do envio ao Kafka.
      */
      public void markInProgress() {
      this.status = Status.IN_PROGRESS.name();
      }

  /**
    * Marca como publicada com timestamp e limpa erros.
      */
      public void markPublished() {
      this.status = Status.PUBLISHED.name();
      this.publishedAt = Instant.now();
      this.lastError = null;
      this.nextAttemptAt = this.publishedAt;
      }

  /**
    * Marca como falha e agenda novo envio com backoff.
      */
      public void markFailed(String error) {
      this.status = Status.FAILED.name();
      this.lastError = error;
      this.retryCount = this.retryCount + 1;
      this.nextAttemptAt = computeBackoff();
      }

  /**
    * Calcula o backoff exponencial baseado no numero de falhas.
      */
      private Instant computeBackoff() {
      long baseSeconds = 5;
      long maxSeconds = 3600;
      long delaySeconds = (long) Math.min(maxSeconds, baseSeconds * Math.pow(2, Math.max(0, retryCount - 1)));
      return Instant.now().plusSeconds(delaySeconds);
      }
      }
      O ChatGPT disse:
      Perfeito — agora dá pra encaixar 100% sem suposição. Seu OutboxMessageJpaEntity já está redondinho e o Inventory só precisa reaproveitar o mesmo modelo (trocar a package) e gravar mensagens com:

aggregateType = "Order" (porque os comandos/eventos são por orderId e a saga usa isso)

aggregateId = orderId

eventType = inventory.reserved|inventory.rejected|inventory.released

topic = inventory.events.v1

payloadJson = JSON do DTO do evento V1

headersJson = JSON do map de headers (SagaHeaders)

Aí o OutboxRelay (igual ao teu) publica no Kafka.

Abaixo vão as peças exatas do EventPublisher + OutboxEventPublisherAdapter para o ms-inventory, compatíveis com essa entidade.

1) Port out: EventPublisher
   📍 com/mvbr/retailstore/inventory/application/port/out/EventPublisher.java

package com.mvbr.retailstore.inventory.application.port.out;

import java.util.Map;

public interface EventPublisher {

    /**
     * Publica um evento via outbox (persistindo em DB).
     *
     * @param topic       topico Kafka (ex: inventory.events.v1)
     * @param aggregateType tipo de agregado (ex: "Order")
     * @param aggregateId id do agregado (ex: orderId)
     * @param eventType   tipo do evento (ex: inventory.reserved)
     * @param payload     payload (DTO serializavel em JSON)
     * @param headers     headers (saga/correlation/causation/etc)
     * @param occurredAt  timestamp do evento (string ISO-8601 ou Instant)
     */
    void publish(String topic,
                 String aggregateType,
                 String aggregateId,
                 String eventType,
                 Object payload,
                 Map<String, String> headers,
                 java.time.Instant occurredAt);
}
2) OutboxJpaRepository (igual seu padrão)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/outbox/OutboxJpaRepository.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    @Query("""
           select m
           from OutboxMessageJpaEntity m
           where m.status in ('PENDING','FAILED')
             and m.nextAttemptAt <= :now
           order by m.createdAt asc
           """)
    List<OutboxMessageJpaEntity> findDue(Instant now);
}
Se no teu checkout você tem outro método (ex.: findDueForUpdate), copia igual. Eu mantive simples aqui.

3) ✅ Adapter: OutboxEventPublisherAdapter (compatível com sua entidade)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/outbox/OutboxEventPublisherAdapter.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxMessageJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(String topic,
                        String aggregateType,
                        String aggregateId,
                        String eventType,
                        Object payload,
                        Map<String, String> headers,
                        Instant occurredAt) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = objectMapper.writeValueAsString(headers);

            String eventId = extractEventId(payload).orElse(UUID.randomUUID().toString());

            OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    payloadJson,
                    headersJson,
                    occurredAt != null ? occurredAt : Instant.now()
            );

            outboxRepository.save(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write outbox message for eventType=" + eventType, e);
        }
    }

    private java.util.Optional<String> extractEventId(Object payload) {
        // Se seu DTO tiver campo eventId (record: eventId()), isso pega automaticamente.
        try {
            var m = payload.getClass().getMethod("eventId");
            Object v = m.invoke(payload);
            if (v != null) return java.util.Optional.of(v.toString());
        } catch (Exception ignored) { }
        return java.util.Optional.empty();
    }
}
Por que ficou perfeito pra você?

usa o teu construtor new OutboxMessageJpaEntity(...)

preenche exatamente os campos que você exige

serializa payloadJson e headersJson

eventId tenta pegar do DTO (eventId()), senão gera UUID

tudo transacionado

4) DTOs de eventos V1 (saída)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/dto/InventoryReservedEventV1.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record InventoryReservedEventV1(
String eventId,
String occurredAt,
String orderId,
String expiresAt,
List<Item> items
) {
public record Item(String productId, long quantity) {}
}
📍 InventoryRejectedEventV1.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryRejectedEventV1(
String eventId,
String occurredAt,
String orderId,
String reason
) {}
📍 InventoryReleasedEventV1.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryReleasedEventV1(
String eventId,
String occurredAt,
String orderId,
String reason
) {}
5) Um publisher “facilitador” pro Inventory (pra você não repetir params)
   Isso aqui é opcional, mas deixa seu serviço limpo:

📍 com/mvbr/retailstore/inventory/application/service/InventoryEventSender.java

package com.mvbr.retailstore.inventory.application.service;

import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.TopicNames;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReleasedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.headers.HeaderNames;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class InventoryEventSender {

    private static final String AGGREGATE_TYPE = "Order";

    private final EventPublisher eventPublisher;

    public InventoryEventSender(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishReserved(String orderId, String expiresAt, InventoryReservedEventV1 payload, Map<String, String> headers) {
        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.reserved",
                payload,
                headers,
                Instant.now()
        );
    }

    public void publishRejected(String orderId, String reason, Map<String, String> headers) {
        InventoryRejectedEventV1 payload = new InventoryRejectedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                reason
        );
        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.rejected",
                payload,
                headers,
                Instant.now()
        );
    }

    public void publishReleased(String orderId, String reason, Map<String, String> headers) {
        InventoryReleasedEventV1 payload = new InventoryReleasedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                reason
        );
        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.released",
                payload,
                headers,
                Instant.now()
        );
    }
}
(Se você preferir, dá pra fazer isso direto no InventoryCommandService.)

6) Como isso se encaixa no teu fluxo
   InventoryCommandConsumer recebe inventory.reserve

ReserveInventoryUseCase chama InventoryCommandService.reserve(...)

ao final:

eventPublisher.publish(topic=inventory.events.v1, eventType=inventory.reserved, ...)

isso grava outbox_messages

OutboxRelay publica de forma confiável no Kafka

Próximo bloco que falta (pra ficar “rodando”)
Agora falta eu te entregar as entidades JPA do inventory e a lógica transacional de:

InventoryItem (on_hand, reserved) com lock

Reservation + ReservationItem com status/TTL

ProcessedMessage para idempotência

Se você colar aqui o teu OutboxRelay do checkout (só a classe), eu deixo o ms-inventory com um relay idêntico (mesmo padrão de publish, headers, status transitions) — sem nenhum “achismo”.

Você disse:
package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

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
/**
* Job que publica mensagens da outbox no Kafka.
* Fluxo: tabela outbox -> OutboxRelay -> Kafka.
  */
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

  /**
    * Varre a outbox periodicamente, publica mensagens e atualiza status.
      */
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

  /**
    * Converte o JSON de headers persistido para map.
      */
      private Map<String, String> parseHeaders(OutboxMessageJpaEntity msg) {
      try {
      return objectMapper.readValue(msg.getHeadersJson(), new TypeReference<>() {});
      } catch (Exception e) {
      throw new IllegalStateException("Could not parse headers for outbox id=" + msg.getId(), e);
      }
      }
      }
      O ChatGPT disse:
      Perfeito — teu OutboxRelay está excelente e dá pra copiar 1:1 pro ms-inventory (só troca a package). O principal é: ele publica exatamente topic, usa aggregateId como key e injeta headers_json no ProducerRecord. Isso garante que a saga no checkout vai receber os headers certinhos.

Vou te entregar agora o pacote outbox completo do ms-inventory compatível com o que você colou:

OutboxMessageJpaEntity (igual)

OutboxJpaRepository (com o método findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc)

OutboxRelay (igual)

OutboxRetentionJob (job simples pra limpeza)

e o OutboxEventPublisherAdapter já no formato certo (usando o construtor da entidade)

Tudo com a package raiz do Inventory.

1) OutboxMessageJpaEntity (ms-inventory)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/outbox/OutboxMessageJpaEntity.java

Copia igual ao teu, só muda a package:

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox;

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

    public void markInProgress() {
        this.status = Status.IN_PROGRESS.name();
    }

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
2) OutboxJpaRepository (ms-inventory)
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/outbox/OutboxJpaRepository.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    List<OutboxMessageJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> status,
            Instant now
    );

    long deleteByStatusInAndCreatedAtBefore(List<String> status, Instant cutoff);
}
3) ✅ OutboxRelay (ms-inventory) — cópia 1:1
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/outbox/OutboxRelay.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox;

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
4) OutboxRetentionJob (ms-inventory) — limpeza simples e segura
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/outbox/OutboxRetentionJob.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

@Component
public class OutboxRetentionJob {

    private static final Logger log = Logger.getLogger(OutboxRetentionJob.class.getName());

    private final OutboxJpaRepository outboxRepository;

    public OutboxRetentionJob(OutboxJpaRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Remove mensagens antigas já publicadas (ou falhas antigas), para não crescer infinito.
     */
    @Scheduled(fixedDelayString = "${outbox.retention.fixedDelayMs:600000}") // 10 min
    @Transactional
    public void cleanup() {
        int days = Integer.parseInt(System.getProperty("outbox.retention.days", "7"));
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        long deleted = outboxRepository.deleteByStatusInAndCreatedAtBefore(
                List.of(
                        OutboxMessageJpaEntity.Status.PUBLISHED.name(),
                        OutboxMessageJpaEntity.Status.FAILED.name()
                ),
                cutoff
        );

        if (deleted > 0) {
            log.info("OutboxRetentionJob cleanup - deleted: " + deleted);
        }
    }
}
Você pode trocar esse System.getProperty por @Value/@ConfigurationProperties, seguindo teu padrão.

5) ✅ OutboxEventPublisherAdapter (ms-inventory) com seu construtor
   📍 com/mvbr/retailstore/inventory/infrastructure/adapter/out/messaging/outbox/OutboxEventPublisherAdapter.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxMessageJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(String topic,
                        String aggregateType,
                        String aggregateId,
                        String eventType,
                        Object payload,
                        Map<String, String> headers,
                        Instant occurredAt) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = objectMapper.writeValueAsString(headers);

            String eventId = extractEventId(payload).orElse(UUID.randomUUID().toString());

            OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    payloadJson,
                    headersJson,
                    occurredAt != null ? occurredAt : Instant.now()
            );

            outboxRepository.save(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write outbox message for eventType=" + eventType, e);
        }
    }

    private Optional<String> extractEventId(Object payload) {
        // tenta payload.eventId() (record) ou payload.getEventId() (bean)
        try {
            var m = payload.getClass().getMethod("eventId");
            Object v = m.invoke(payload);
            if (v != null) return Optional.of(v.toString());
        } catch (Exception ignored) { }

        try {
            var m = payload.getClass().getMethod("getEventId");
            Object v = m.invoke(payload);
            if (v != null) return Optional.of(v.toString());
        } catch (Exception ignored) { }

        return Optional.empty();
    }
}
Com isso, o ms-inventory já tem o “caminho confiável” de saída completo ✅
Agora falta fechar o “coração” do Inventory:

InventoryItem / Reservation / ReservationItem (domínio)

Entidades JPA + repositories com lock (SELECT FOR UPDATE)

ProcessedMessage (idempotência)

InventoryCommandService.reserve/release

ReservationExpirationJob (auto-heal)

Se você quiser, eu continuo agora e te passo:

as entidades JPA (inventory_items, inventory_reservations, inventory_reservation_items, processed_messages)

os repositórios Spring Data com um método @Lock(PESSIMISTIC_WRITE) pra buscar por productIds

e o InventoryCommandService implementando reserva/liberação com idempotência

Só me diz qual abordagem você prefere para concorrência no estoque:

A) Pessimistic lock (FOR UPDATE) — mais simples e forte no Postgres

B) Optimistic (@Version) com retry — mais elegante, mais trabalho

Você disse:
Quero. ) Pessimistic lock (FOR UPDATE) — mais simples e forte no Postgres
O ChatGPT disse:
Fechado. Vamos de pessimistic lock (FOR UPDATE) — é o caminho mais “tanque de guerra” no Postgres e fica simples de raciocinar.

A seguir eu te entrego o núcleo inteiro do ms-inventory (sem web), já no teu padrão:

DDL mental → entidades JPA equivalentes

Spring Data repositories com @Lock(PESSIMISTIC_WRITE)

ProcessedMessage pra idempotência

InventoryCommandService com reserve() e release() atômicos

ReservationExpirationJob (auto-heal)

Tudo em com.mvbr.retailstore.inventory...

1) Domain (model) — simples e útil
   1.1 VOs
   📍 domain/model/ProductId.java

package com.mvbr.retailstore.inventory.domain.model;

import java.util.Objects;

public record ProductId(String value) {
public ProductId {
if (value == null || value.isBlank()) throw new IllegalArgumentException("productId is required");
}
@Override public String toString() { return value; }
}
📍 domain/model/OrderId.java

package com.mvbr.retailstore.inventory.domain.model;

public record OrderId(String value) {
public OrderId {
if (value == null || value.isBlank()) throw new IllegalArgumentException("orderId is required");
}
@Override public String toString() { return value; }
}
📍 domain/model/Quantity.java

package com.mvbr.retailstore.inventory.domain.model;

public record Quantity(long value) {
public Quantity {
if (value <= 0) throw new IllegalArgumentException("quantity must be > 0");
}
}
1.2 InventoryItem (saldo)
📍 domain/model/InventoryItem.java

package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;

public class InventoryItem {

    private final ProductId productId;
    private long onHand;
    private long reserved;
    private Instant updatedAt;

    public InventoryItem(ProductId productId, long onHand, long reserved, Instant updatedAt) {
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = reserved;
        this.updatedAt = updatedAt;
        validateNonNegative();
    }

    public ProductId getProductId() { return productId; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public Instant getUpdatedAt() { return updatedAt; }

    public long available() {
        return onHand - reserved;
    }

    public void reserve(long qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
        if (available() < qty) throw new IllegalStateException("insufficient stock");
        this.reserved += qty;
        this.updatedAt = Instant.now();
        validateNonNegative();
    }

    public void release(long qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
        if (this.reserved < qty) throw new IllegalStateException("reserved underflow");
        this.reserved -= qty;
        this.updatedAt = Instant.now();
        validateNonNegative();
    }

    private void validateNonNegative() {
        if (onHand < 0 || reserved < 0) throw new IllegalStateException("negative stock fields");
        if (reserved > onHand) {
            // opcional: dependendo da política, você pode permitir reserved > onHand em backorder.
            throw new IllegalStateException("reserved cannot exceed onHand");
        }
    }
}
1.3 Reservation
📍 domain/model/ReservationStatus.java

package com.mvbr.retailstore.inventory.domain.model;

public enum ReservationStatus {
PENDING,
RESERVED,
REJECTED,
RELEASED,
EXPIRED
}
📍 domain/model/ReservationItem.java

package com.mvbr.retailstore.inventory.domain.model;

public record ReservationItem(ProductId productId, Quantity quantity) { }
📍 domain/model/Reservation.java

package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Reservation {

    private final String reservationId;
    private final OrderId orderId;
    private ReservationStatus status;
    private String reason;
    private final Instant createdAt;
    private Instant expiresAt;
    private String lastCommandId;
    private String correlationId;

    private final List<ReservationItem> items = new ArrayList<>();

    public Reservation(String reservationId,
                       OrderId orderId,
                       ReservationStatus status,
                       String reason,
                       Instant createdAt,
                       Instant expiresAt,
                       String lastCommandId,
                       String correlationId) {
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastCommandId = lastCommandId;
        this.correlationId = correlationId;
    }

    public String getReservationId() { return reservationId; }
    public OrderId getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getLastCommandId() { return lastCommandId; }
    public String getCorrelationId() { return correlationId; }
    public List<ReservationItem> getItems() { return Collections.unmodifiableList(items); }

    public boolean isReserved() { return status == ReservationStatus.RESERVED; }
    public boolean isClosed() {
        return status == ReservationStatus.RELEASED
                || status == ReservationStatus.REJECTED
                || status == ReservationStatus.EXPIRED;
    }

    public void addItem(ProductId productId, Quantity quantity) {
        items.add(new ReservationItem(productId, quantity));
    }

    public void markReserved() {
        this.status = ReservationStatus.RESERVED;
        this.reason = null;
    }

    public void markRejected(String reason) {
        this.status = ReservationStatus.REJECTED;
        this.reason = reason;
    }

    public void markReleased(String reason) {
        this.status = ReservationStatus.RELEASED;
        this.reason = reason;
    }

    public void markExpired() {
        this.status = ReservationStatus.EXPIRED;
        this.reason = "EXPIRED";
    }

    public void updateExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void updateLastCommandId(String commandId) { this.lastCommandId = commandId; }
}
2) JPA Entities (persistence) + repositories com FOR UPDATE
   2.1 Inventory item entity
   📍 infrastructure/adapter/out/persistence/JpaInventoryItemEntity.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory_items")
public class JpaInventoryItemEntity {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "on_hand", nullable = false)
    private long onHand;

    @Column(name = "reserved", nullable = false)
    private long reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JpaInventoryItemEntity() {}

    public JpaInventoryItemEntity(String productId, long onHand, long reserved, Instant updatedAt) {
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = reserved;
        this.updatedAt = updatedAt;
    }

    public String getProductId() { return productId; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setOnHand(long onHand) { this.onHand = onHand; }
    public void setReserved(long reserved) { this.reserved = reserved; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
2.2 Reservation entities
📍 JpaReservationEntity.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
name = "inventory_reservations",
indexes = {
@Index(name = "idx_inv_res_status_expires", columnList = "status, expires_at"),
@Index(name = "uk_inv_res_order_id", columnList = "order_id", unique = true)
}
)
public class JpaReservationEntity {

    @Id
    @Column(name = "reservation_id", length = 64)
    private String reservationId;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "reason", length = 128)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_command_id", length = 64)
    private String lastCommandId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JpaReservationItemEntity> items = new ArrayList<>();

    protected JpaReservationEntity() {}

    public JpaReservationEntity(String reservationId, String orderId, String status, Instant createdAt, Instant expiresAt) {
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getReservationId() { return reservationId; }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getLastCommandId() { return lastCommandId; }
    public String getCorrelationId() { return correlationId; }
    public List<JpaReservationItemEntity> getItems() { return items; }

    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setLastCommandId(String lastCommandId) { this.lastCommandId = lastCommandId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public void addItem(JpaReservationItemEntity item) {
        items.add(item);
        item.setReservation(this);
    }

    public void clearItems() {
        items.forEach(i -> i.setReservation(null));
        items.clear();
    }
}
📍 JpaReservationItemEntity.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_reservation_items",
indexes = @Index(name = "idx_inv_res_item_reservation", columnList = "reservation_id"))
public class JpaReservationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private JpaReservationEntity reservation;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    protected JpaReservationItemEntity() {}

    public JpaReservationItemEntity(String productId, long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public JpaReservationEntity getReservation() { return reservation; }
    public String getProductId() { return productId; }
    public long getQuantity() { return quantity; }

    public void setReservation(JpaReservationEntity reservation) { this.reservation = reservation; }
}
2.3 Processed message (idempotência)
📍 JpaProcessedMessageEntity.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_messages")
public class JpaProcessedMessageEntity {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "message_type", nullable = false, length = 64)
    private String messageType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected JpaProcessedMessageEntity() {}

    public JpaProcessedMessageEntity(String messageId, String messageType, String aggregateId, Instant processedAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.aggregateId = aggregateId;
        this.processedAt = processedAt;
    }

    public String getMessageId() { return messageId; }
}
3) Spring Data repositories (com lock FOR UPDATE)
   📍 JpaInventorySpringDataRepository.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;

public interface JpaInventorySpringDataRepository extends JpaRepository<JpaInventoryItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JpaInventoryItemEntity i where i.productId in :productIds")
    List<JpaInventoryItemEntity> lockByProductIds(List<String> productIds);
}
📍 JpaReservationSpringDataRepository.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JpaReservationSpringDataRepository extends JpaRepository<JpaReservationEntity, String> {

    Optional<JpaReservationEntity> findByOrderId(String orderId);

    @Query("""
           select r from JpaReservationEntity r
           where r.status = 'RESERVED'
             and r.expiresAt <= :now
           order by r.expiresAt asc
           """)
    List<JpaReservationEntity> findExpiredReserved(Instant now);
}
📍 JpaProcessedMessageSpringDataRepository.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedMessageSpringDataRepository extends JpaRepository<JpaProcessedMessageEntity, String> {
}
4) Adapters (ports out) — InventoryRepository / ReservationRepository / ProcessedMessageRepository
   📍 application/port/out/InventoryItemRepository.java

package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.model.InventoryItem;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository {
List<InventoryItem> lockByProductIds(List<String> productIds);
Optional<InventoryItem> findByProductId(String productId);
InventoryItem save(InventoryItem item);
}
📍 application/port/out/ReservationRepository.java

package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.model.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
Optional<Reservation> findByOrderId(String orderId);
Reservation save(Reservation reservation);
List<Reservation> findExpiredReserved(Instant now, int limit);
}
📍 application/port/out/ProcessedMessageRepository.java

package com.mvbr.retailstore.inventory.application.port.out;

import java.time.Instant;

public interface ProcessedMessageRepository {
boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt);
}
Adapter JPA (impl)
📍 infrastructure/adapter/out/persistence/JpaInventoryRepositoryAdapter.java

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.inventory.application.port.out.InventoryItemRepository;
import com.mvbr.retailstore.inventory.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.inventory.application.port.out.ReservationRepository;
import com.mvbr.retailstore.inventory.domain.model.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JpaInventoryRepositoryAdapter implements InventoryItemRepository, ReservationRepository, ProcessedMessageRepository {

    private final JpaInventorySpringDataRepository inventoryRepo;
    private final JpaReservationSpringDataRepository reservationRepo;
    private final JpaProcessedMessageSpringDataRepository processedRepo;

    public JpaInventoryRepositoryAdapter(JpaInventorySpringDataRepository inventoryRepo,
                                        JpaReservationSpringDataRepository reservationRepo,
                                        JpaProcessedMessageSpringDataRepository processedRepo) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
        this.processedRepo = processedRepo;
    }

    // ---------------- InventoryItemRepository ----------------

    @Override
    public List<InventoryItem> lockByProductIds(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        List<JpaInventoryItemEntity> entities = inventoryRepo.lockByProductIds(productIds);

        // IMPORTANTE: se algum produto não existir ainda, o lock não vai trazer.
        // Para MVP, você pode:
        // 1) rejeitar se não existe
        // 2) ou criar com on_hand=0 (que vai rejeitar de qualquer jeito)
        Map<String, JpaInventoryItemEntity> map = entities.stream()
                .collect(Collectors.toMap(JpaInventoryItemEntity::getProductId, e -> e));

        List<InventoryItem> result = new ArrayList<>();
        for (String pid : productIds) {
            JpaInventoryItemEntity e = map.get(pid);
            if (e == null) {
                // cria "virtual" com 0 (não persiste aqui; serviço decide)
                result.add(new InventoryItem(new ProductId(pid), 0, 0, Instant.now()));
            } else {
                result.add(toDomain(e));
            }
        }
        return result;
    }

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        return inventoryRepo.findById(productId).map(this::toDomain);
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        JpaInventoryItemEntity e = new JpaInventoryItemEntity(
                item.getProductId().value(),
                item.getOnHand(),
                item.getReserved(),
                item.getUpdatedAt()
        );
        inventoryRepo.save(e);
        return item;
    }

    // ---------------- ReservationRepository ----------------

    @Override
    public Optional<Reservation> findByOrderId(String orderId) {
        return reservationRepo.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Reservation save(Reservation reservation) {
        JpaReservationEntity e = reservationRepo.findById(reservation.getReservationId())
                .orElseGet(() -> new JpaReservationEntity(
                        reservation.getReservationId(),
                        reservation.getOrderId().value(),
                        reservation.getStatus().name(),
                        reservation.getCreatedAt(),
                        reservation.getExpiresAt()
                ));

        e.setStatus(reservation.getStatus().name());
        e.setReason(reservation.getReason());
        e.setExpiresAt(reservation.getExpiresAt());
        e.setLastCommandId(reservation.getLastCommandId());
        e.setCorrelationId(reservation.getCorrelationId());

        // Rebuild itens
        e.clearItems();
        for (ReservationItem item : reservation.getItems()) {
            e.addItem(new JpaReservationItemEntity(item.productId().value(), item.quantity().value()));
        }

        reservationRepo.save(e);
        return reservation;
    }

    @Override
    public List<Reservation> findExpiredReserved(Instant now, int limit) {
        List<JpaReservationEntity> list = reservationRepo.findExpiredReserved(now);
        if (limit > 0 && list.size() > limit) list = list.subList(0, limit);
        return list.stream().map(this::toDomain).toList();
    }

    // ---------------- ProcessedMessageRepository ----------------

    @Override
    public boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt) {
        try {
            processedRepo.save(new JpaProcessedMessageEntity(messageId, messageType, aggregateId, processedAt));
            return true;
        } catch (DataIntegrityViolationException e) {
            // PK duplicate -> já processado
            return false;
        }
    }

    // ---------------- Mappers ----------------

    private InventoryItem toDomain(JpaInventoryItemEntity e) {
        return new InventoryItem(new ProductId(e.getProductId()), e.getOnHand(), e.getReserved(), e.getUpdatedAt());
    }

    private Reservation toDomain(JpaReservationEntity e) {
        Reservation r = new Reservation(
                e.getReservationId(),
                new OrderId(e.getOrderId()),
                ReservationStatus.valueOf(e.getStatus()),
                e.getReason(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getLastCommandId(),
                e.getCorrelationId()
        );

        for (JpaReservationItemEntity item : e.getItems()) {
            r.addItem(new ProductId(item.getProductId()), new Quantity(item.getQuantity()));
        }

        return r;
    }
}
Sim, eu juntei os 3 ports num adapter só, porque no MVP isso reduz boilerplate. Se você quiser 3 adapters separados, também fica lindo.

5) InventoryCommandService (o coração)
   📍 application/service/InventoryCommandService.java

package com.mvbr.retailstore.inventory.application.service;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.ReserveInventoryItemCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.application.port.out.InventoryItemRepository;
import com.mvbr.retailstore.inventory.application.port.out.ProcessedMessageRepository;
import com.mvbr.retailstore.inventory.application.port.out.ReservationRepository;
import com.mvbr.retailstore.inventory.domain.model.*;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.TopicNames;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryRejectedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReleasedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReservedEventV1;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.headers.HeaderNames;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

@Component
public class InventoryCommandService {

    private static final Logger log = Logger.getLogger(InventoryCommandService.class.getName());

    private static final String AGGREGATE_TYPE = "Order";

    private final InventoryItemRepository inventoryRepo;
    private final ReservationRepository reservationRepo;
    private final ProcessedMessageRepository processedRepo;
    private final EventPublisher eventPublisher;

    private final long reservationTtlSeconds;

    public InventoryCommandService(InventoryItemRepository inventoryRepo,
                                   ReservationRepository reservationRepo,
                                   ProcessedMessageRepository processedRepo,
                                   EventPublisher eventPublisher) {
        this.inventoryRepo = inventoryRepo;
        this.reservationRepo = reservationRepo;
        this.processedRepo = processedRepo;
        this.eventPublisher = eventPublisher;
        this.reservationTtlSeconds = Long.getLong("inventory.reservation.ttlSeconds", 120L);
    }

    @Transactional
    public void reserve(ReserveInventoryCommand cmd, SagaContext ctx) {

        String orderId = cmd.orderId();
        String commandId = cmd.commandId();

        // 1) Idempotência por commandId
        boolean first = processedRepo.markProcessedIfFirst(commandId, "inventory.reserve", orderId, Instant.now());
        if (!first) {
            reservationRepo.findByOrderId(orderId).ifPresentOrElse(
                    existing -> republishReserveOutcome(existing, ctx),
                    () -> log.info("Duplicate inventory.reserve but no reservation found. orderId=" + orderId)
            );
            return;
        }

        // 2) Se já existe reserva para orderId, responde idempotente
        Optional<Reservation> existingOpt = reservationRepo.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            republishReserveOutcome(existingOpt.get(), ctx);
            return;
        }

        // 3) Cria reservation PENDING
        Reservation reservation = new Reservation(
                UUID.randomUUID().toString(),
                new OrderId(orderId),
                ReservationStatus.PENDING,
                null,
                Instant.now(),
                Instant.now().plus(reservationTtlSeconds, ChronoUnit.SECONDS),
                commandId,
                ctx.correlationId()
        );

        // 4) Lock estoque dos produtos
        List<String> productIds = cmd.items().stream().map(ReserveInventoryItemCommand::productId).toList();
        List<InventoryItem> stocks = inventoryRepo.lockByProductIds(productIds);
        Map<String, InventoryItem> byProduct = new HashMap<>();
        for (InventoryItem s : stocks) byProduct.put(s.getProductId().value(), s);

        // 5) Validar disponibilidade (tudo ou nada)
        for (ReserveInventoryItemCommand it : cmd.items()) {
            InventoryItem stock = byProduct.get(it.productId());
            if (stock == null) {
                reject(reservation, ctx, orderId, "UNKNOWN_PRODUCT:" + it.productId());
                return;
            }
            if (stock.available() < it.quantity()) {
                reject(reservation, ctx, orderId, "INSUFFICIENT_STOCK:" + it.productId());
                return;
            }
        }

        // 6) Aplicar reserva
        for (ReserveInventoryItemCommand it : cmd.items()) {
            InventoryItem stock = byProduct.get(it.productId());
            stock.reserve(it.quantity());
            inventoryRepo.save(stock);
            reservation.addItem(new ProductId(it.productId()), new Quantity(it.quantity()));
        }

        reservation.markReserved();
        reservationRepo.save(reservation);

        // 7) Publicar inventory.reserved (via outbox)
        publishReserved(reservation, ctx);
    }

    @Transactional
    public void release(ReleaseInventoryCommand cmd, SagaContext ctx) {

        String orderId = cmd.orderId();
        String commandId = cmd.commandId();
        String reason = (cmd.reason() == null || cmd.reason().isBlank()) ? "RELEASED" : cmd.reason();

        boolean first = processedRepo.markProcessedIfFirst(commandId, "inventory.release", orderId, Instant.now());
        if (!first) {
            // idempotente: publica released de novo (ou apenas loga)
            publishReleased(orderId, reason, ctx);
            return;
        }

        Optional<Reservation> reservationOpt = reservationRepo.findByOrderId(orderId);
        if (reservationOpt.isEmpty()) {
            publishReleased(orderId, "NOT_FOUND", ctx);
            return;
        }

        Reservation reservation = reservationOpt.get();

        // se não está RESERVED, é idempotente
        if (!reservation.isReserved()) {
            publishReleased(orderId, "ALREADY_" + reservation.getStatus().name(), ctx);
            return;
        }

        // lock itens do estoque e devolve reserva
        List<String> productIds = reservation.getItems().stream().map(i -> i.productId().value()).toList();
        List<InventoryItem> stocks = inventoryRepo.lockByProductIds(productIds);
        Map<String, InventoryItem> byProduct = new HashMap<>();
        for (InventoryItem s : stocks) byProduct.put(s.getProductId().value(), s);

        for (ReservationItem it : reservation.getItems()) {
            InventoryItem stock = byProduct.get(it.productId().value());
            if (stock == null) {
                // Se sumiu o produto do estoque, melhor falhar pra retry do Kafka (não perder consistência)
                throw new IllegalStateException("Inventory item not found for productId=" + it.productId().value());
            }
            stock.release(it.quantity().value());
            inventoryRepo.save(stock);
        }

        reservation.markReleased(reason);
        reservation.updateLastCommandId(commandId);
        reservationRepo.save(reservation);

        publishReleased(orderId, reason, ctx);
    }

    private void reject(Reservation reservation, SagaContext ctx, String orderId, String reason) {
        reservation.markRejected(reason);
        reservationRepo.save(reservation);
        publishRejected(orderId, reason, ctx);
    }

    private void republishReserveOutcome(Reservation reservation, SagaContext ctx) {
        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            publishReserved(reservation, ctx);
            return;
        }
        if (reservation.getStatus() == ReservationStatus.REJECTED) {
            publishRejected(reservation.getOrderId().value(), reservation.getReason(), ctx);
            return;
        }
        // Se PENDING (muito raro), trata como rejeição operacional
        publishRejected(reservation.getOrderId().value(), "PENDING_STATE", ctx);
    }

    private void publishReserved(Reservation reservation, SagaContext ctx) {
        List<InventoryReservedEventV1.Item> items = reservation.getItems().stream()
                .map(i -> new InventoryReservedEventV1.Item(i.productId().value(), i.quantity().value()))
                .toList();

        InventoryReservedEventV1 event = new InventoryReservedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                reservation.getOrderId().value(),
                reservation.getExpiresAt().toString(),
                items
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                reservation.getOrderId().value(),
                "inventory.reserved",
                event,
                sagaEventHeaders(ctx, "inventory.reserved", reservation.getOrderId().value()),
                Instant.now()
        );
    }

    private void publishRejected(String orderId, String reason, SagaContext ctx) {
        InventoryRejectedEventV1 event = new InventoryRejectedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                reason
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.rejected",
                event,
                sagaEventHeaders(ctx, "inventory.rejected", orderId),
                Instant.now()
        );
    }

    private void publishReleased(String orderId, String reason, SagaContext ctx) {
        InventoryReleasedEventV1 event = new InventoryReleasedEventV1(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                orderId,
                reason
        );

        eventPublisher.publish(
                TopicNames.INVENTORY_EVENTS_V1,
                AGGREGATE_TYPE,
                orderId,
                "inventory.released",
                event,
                sagaEventHeaders(ctx, "inventory.released", orderId),
                Instant.now()
        );
    }

    private Map<String, String> sagaEventHeaders(SagaContext ctx, String eventType, String aggregateId) {
        // Aqui o ideal é você usar a tua classe SagaHeaders (copiar do ms-order/ms-checkout),
        // mas pra não travar, eu monto o map com HeaderNames.
        Map<String, String> h = new HashMap<>();
        h.put(HeaderNames.EVENT_TYPE, eventType);
        h.put(HeaderNames.COMMAND_TYPE, eventType); // você usa isso em alguns pontos (compat)
        if (ctx != null) {
            putIfNotNull(h, HeaderNames.SAGA_ID, ctx.sagaId());
            putIfNotNull(h, HeaderNames.CORRELATION_ID, ctx.correlationId());
            putIfNotNull(h, HeaderNames.CAUSATION_ID, ctx.causationId());
            putIfNotNull(h, HeaderNames.SAGA_NAME, ctx.sagaName());
            putIfNotNull(h, HeaderNames.SAGA_STEP, ctx.sagaStep());
        }
        h.put(HeaderNames.AGGREGATE_TYPE, AGGREGATE_TYPE);
        h.put(HeaderNames.AGGREGATE_ID, aggregateId);
        return h;
    }

    private void putIfNotNull(Map<String, String> map, String k, String v) {
        if (v != null && !v.isBlank()) map.put(k, v);
    }
}
Por que isso fica “blindado” no Postgres?

lockByProductIds() usa PESSIMISTIC_WRITE → o Postgres faz FOR UPDATE nas linhas.

A reserva vira transação única: valida + incrementa reserved + salva reserva + outbox.

Idempotência por processed_messages impede duplicar reserva/release.

6) UseCases (implementações finas)
   📍 application/usecase/ReserveInventoryUseCaseImpl.java

package com.mvbr.retailstore.inventory.application.usecase;

import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReserveInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InventoryCommandService;
import org.springframework.stereotype.Component;

@Component
public class ReserveInventoryUseCaseImpl implements ReserveInventoryUseCase {

    private final InventoryCommandService service;

    public ReserveInventoryUseCaseImpl(InventoryCommandService service) {
        this.service = service;
    }

    @Override
    public void reserve(ReserveInventoryCommand command, SagaContext sagaContext) {
        service.reserve(command, sagaContext);
    }
}
📍 ReleaseInventoryUseCaseImpl.java

package com.mvbr.retailstore.inventory.application.usecase;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InventoryCommandService;
import org.springframework.stereotype.Component;

@Component
public class ReleaseInventoryUseCaseImpl implements ReleaseInventoryUseCase {

    private final InventoryCommandService service;

    public ReleaseInventoryUseCaseImpl(InventoryCommandService service) {
        this.service = service;
    }

    @Override
    public void release(ReleaseInventoryCommand command, SagaContext sagaContext) {
        service.release(command, sagaContext);
    }
}
7) Expiração automática (auto-heal)
   📍 application/service/ReservationExpirationService.java

package com.mvbr.retailstore.inventory.application.service;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.out.ReservationRepository;
import com.mvbr.retailstore.inventory.domain.model.Reservation;
import com.mvbr.retailstore.inventory.domain.model.ReservationStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ReservationExpirationService {

    private final ReservationRepository reservationRepo;
    private final InventoryCommandService inventoryService;

    public ReservationExpirationService(ReservationRepository reservationRepo,
                                        InventoryCommandService inventoryService) {
        this.reservationRepo = reservationRepo;
        this.inventoryService = inventoryService;
    }

    public void expireDue(int batchSize) {
        List<Reservation> expired = reservationRepo.findExpiredReserved(Instant.now(), batchSize);
        for (Reservation r : expired) {
            // Idempotente: se já foi liberada, o release ignora.
            ReleaseInventoryCommand cmd = new ReleaseInventoryCommand(
                    UUID.randomUUID().toString(),
                    r.getOrderId().value(),
                    "EXPIRED"
            );
            inventoryService.release(cmd, new SagaContext(null, r.getCorrelationId(), null, "inventory", "EXPIRE", "Order", r.getOrderId().value()));
        }
    }
}
📍 infrastructure/adapter/out/persistence não precisa de nada extra — já temos query findExpiredReserved.

📍 infrastructure/adapter/out/scheduling/ReservationExpirationJob.java (ou config/SchedulingConfig)

package com.mvbr.retailstore.inventory.infrastructure.adapter.out.scheduling;

import com.mvbr.retailstore.inventory.application.service.ReservationExpirationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationExpirationJob {

    private final ReservationExpirationService service;

    public ReservationExpirationJob(ReservationExpirationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${inventory.expiration.scanFixedDelayMs:5000}")
    @Transactional
    public void tick() {
        int batchSize = Integer.parseInt(System.getProperty("inventory.expiration.batchSize", "50"));
        service.expireDue(batchSize);
    }
}
8) Observação crucial (bem importante)
   No lockByProductIds() eu coloquei uma política simples: se o produto não existir, eu devolvo um InventoryItem “virtual” com onHand=0 pra forçar rejeição.

Em produção, normalmente você:

pré-carrega inventory_items via evento de catálogo/ERP

ou tem endpoint/admin pra criar SKU

Mas pro teu MVP, essa estratégia é ótima: não existe SKU → rejeita.

Próximo passo (pra você rodar de verdade)
Me diga como você quer “seedar” o estoque pra testar:

SQL seed (insert em inventory_items) no startup (Flyway/Liquibase)
ou

Um command interno “inventory.stock.upsert” só pra ambiente dev

Eu recomendo 1) SQL seed pro MVP (bem mais rápido).

Você disse:
Eu quero, recomendo 1) SQL seed pro MVP (bem mais rápido). 20 registros
O ChatGPT disse:
Fechado — seed via SQL é o melhor pro MVP mesmo. Vou te passar um seed com 20 registros pra tabela inventory_items, já no estilo “pode rodar várias vezes sem dar pau” (com UPSERT).

✅ Importante: os product_id aqui precisam bater com os productId que você manda no PlaceOrder / checkout (senão vai rejeitar por “insufficient stock/unknown product”).

Opção recomendada: Flyway
Cria o arquivo:

ms-inventory/src/main/resources/db/migration/V2__seed_inventory_items.sql

(Se você ainda não usa Flyway, dá pra rodar no psql/DBeaver também.)

-- Seed MVP: 20 produtos no estoque
-- Tabela esperada: inventory_items(product_id, on_hand, reserved, updated_at)

INSERT INTO inventory_items (product_id, on_hand, reserved, updated_at) VALUES
('P-0001', 120, 0, now()),
('P-0002',  80, 0, now()),
('P-0003',  50, 0, now()),
('P-0004',  35, 0, now()),
('P-0005', 200, 0, now()),
('P-0006',  15, 0, now()),
('P-0007',  60, 0, now()),
('P-0008',  90, 0, now()),
('P-0009',  25, 0, now()),
('P-0010',  10, 0, now()),
('P-0011', 300, 0, now()),
('P-0012',  40, 0, now()),
('P-0013',  75, 0, now()),
('P-0014',  55, 0, now()),
('P-0015',  20, 0, now()),
('P-0016', 150, 0, now()),
('P-0017',   8, 0, now()),
('P-0018', 110, 0, now()),
('P-0019',  65, 0, now()),
('P-0020',  95, 0, now())
ON CONFLICT (product_id)
DO UPDATE SET
on_hand   = EXCLUDED.on_hand,
reserved  = EXCLUDED.reserved,
updated_at = EXCLUDED.updated_at;
Por que esse seed é “bom”?
reserved = 0 pra começar limpo

ON CONFLICT deixa você rodar de novo sem erro (e sempre volta o estoque pro padrão do seed)

os estoques variam (dá pra testar rejeição e sucesso)

Dica rápida pra testar no Postman
Quando for criar um pedido/checkout, use productId como P-0001…P-0020 e quantidades tipo 1..5.

E pra testar inventory.rejected, manda um item com:

productId = P-0017 e quantity = 999 (vai falhar na hora)

Se você quiser, cola aqui como você está criando os itens do pedido (o DTO do PlaceOrderItemCommand ou o payload do Postman) e eu te devolvo um exemplo de request completo usando esses SKUs, já encaixado no teu fluxo ms-order → checkout → inventory.

Você disse:
package com.mvbr.retailstore.order.application.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record PlaceOrderCommand(
String customerId,
List<PlaceOrderItemCommand> items,
Optional<BigDecimal> discount
) {

    public PlaceOrderCommand toCreateOrder() {
        List<PlaceOrderItemCommand> orderItems = items.stream()
                .map(item -> new PlaceOrderItemCommand(
                        item.productId(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        return new PlaceOrderCommand(
                customerId,
                orderItems,
                discount
        );
    }
}      .... package com.mvbr.retailstore.order.application.command;

import java.math.BigDecimal;

public record PlaceOrderItemCommand(
String productId,
int quantity,
BigDecimal price
) { }
O ChatGPT disse:
Perfeito — então teu productId é String, e o Inventory vai reconhecer exatamente os SKUs do seed (P-0001…P-0020). Bora te passar um exemplo pronto de Postman (request do ms-order) que vai disparar a saga e chegar no Inventory com os mesmos ids.

Vou assumir que teu endpoint de criação está algo tipo POST /api/orders (ou parecido). Se o path for outro, você só troca a URL.

1) Seed: mantenha esses SKUs
   Use esses productId no PlaceOrderItemCommand:

P-0001 … P-0020

2) Exemplo de request (sucesso)
   POST (ms-order)
   POST http://localhost:8081/api/orders
   Content-Type: application/json
   {
   "customerId": "C-1000",
   "items": [
   { "productId": "P-0001", "quantity": 2, "price": 19.90 },
   { "productId": "P-0007", "quantity": 1, "price": 59.90 },
   { "productId": "P-0016", "quantity": 3, "price": 9.90 }
   ],
   "discount": 5.00
   }
   Esse pedido deve:

criar o Order

checkout vai enviar inventory.reserve com productId iguais

inventory reserva e publica inventory.reserved

3) Exemplo de request (falha de estoque → inventory.rejected)
   Escolhe um SKU com pouco estoque no seed (ex: P-0017 tem 8):

{
"customerId": "C-1001",
"items": [
{ "productId": "P-0017", "quantity": 999, "price": 3.50 }
],
"discount": 0
}
Resultado esperado no fluxo:

inventory vai rejeitar com reason tipo INSUFFICIENT_STOCK:P-0017

checkout vai compensar e cancelar o pedido

4) Dica valiosa: alinhar tipos int vs long
   No ms-order o quantity é int.
   No inventory eu usei long (quantity em commands/events). Isso não quebra JSON (Jackson converte), mas eu recomendo padronizar pra long em tudo ou int em tudo pra reduzir atrito.

Se quiser “padrão enterprise”, eu iria de long em todo lugar (quantidade pode crescer em cenários B2B).

5) Dica extra (muito importante): consistência productId entre serviços
   Pra evitar “product não existe” no inventory, tenha uma regra:

ms-order só aceita productId que segue o padrão P-\\d{4} no MVP

Você pode validar no PlaceOrderCommandService ou no OrderWebMapper antes de criar o pedido.

Se você me colar o teu endpoint exato do OrderCommandController (só o método do POST), eu ajusto o request acima com URL + payload 100% idênticos ao teu controller, sem adivinhação.

Você disse:
package com.mvbr.retailstore.order.infrastructure.adapter.in.web.controller;

import com.mvbr.retailstore.order.application.port.in.CancelOrderUseCase;
import com.mvbr.retailstore.order.application.port.in.PlaceOrderUseCase;
import com.mvbr.retailstore.order.application.port.in.ConfirmOrderUseCase;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto.OrderCreatedResponse;
import com.mvbr.retailstore.order.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderCommandController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderWebMapper mapper;

    public OrderCommandController(PlaceOrderUseCase placeOrderUseCase,
                                  ConfirmOrderUseCase confirmOrderUseCase,
                                  CancelOrderUseCase cancelOrderUseCase,
                                  OrderWebMapper mapper) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.mapper = mapper;
    }


    @PostMapping
    public ResponseEntity<?> place(@Valid @RequestBody CreateOrderRequest request) {
        var orderId = placeOrderUseCase.execute(mapper.toPlaceOrderCommand(request));
        return ResponseEntity
                .created(URI.create("/orders/" + orderId.value()))
                .body(new OrderCreatedResponse(orderId.value()));
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable String orderId) {
        var order = confirmOrderUseCase.confirm(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String orderId) {
        var order = cancelOrderUseCase.cancel(orderId);
        return ResponseEntity.ok(mapper.toResponse(order));
    }
}
O ChatGPT disse:
Boa — então teu endpoint exato é:

POST /orders (cria)

POST /orders/{orderId}/confirm

POST /orders/{orderId}/cancel

Agora sim dá pra te passar requests 100% compatíveis.

1) Postman/cURL — criar pedido (sucesso)
   cURL
   curl -i -X POST "http://localhost:8081/orders" \
   -H "Content-Type: application/json" \
   -d '{
   "customerId": "C-1000",
   "items": [
   { "productId": "P-0001", "quantity": 2, "price": 19.90 },
   { "productId": "P-0007", "quantity": 1, "price": 59.90 },
   { "productId": "P-0016", "quantity": 3, "price": 9.90 }
   ],
   "discount": 5.00
   }'
   Resposta esperada
   201 Created

Location: /orders/<orderId>

body:

{ "orderId": "<orderId>" }
Use esse <orderId> depois para confirm / cancel.

2) Criar pedido (falha de estoque → deve virar inventory.rejected)
   Se você seedou P-0017 com estoque 8 (como sugeri), isso falha:

curl -i -X POST "http://localhost:8081/orders" \
-H "Content-Type: application/json" \
-d '{
"customerId": "C-1001",
"items": [
{ "productId": "P-0017", "quantity": 999, "price": 3.50 }
],
"discount": 0
}'
O ms-order ainda vai criar o pedido (dependendo do teu fluxo), mas a saga deve compensar e depois você vai ver o status CANCELLED/CANCELED (conforme teu enum) depois que o checkout processar o rejeitado.

3) Confirmar pedido (manual)
   Se no teu fluxo o pedido nasce DRAFT e precisa confirmação:

curl -i -X POST "http://localhost:8081/orders/<orderId>/confirm"
4) Cancelar pedido (manual)
   curl -i -X POST "http://localhost:8081/orders/<orderId>/cancel"
5) Checklist de compatibilidade com o Inventory (pra não “morrer silencioso”)
   A) O CreateOrderRequest tem que aceitar esse JSON
   Ele precisa ter:

customerId (String)

items[] com productId, quantity, price

discount (opcional)

Se discount for Optional<BigDecimal> no command, no request pode ser null ou omitido. Exemplo omitindo:

{
"customerId": "C-1002",
"items": [
{ "productId": "P-0002", "quantity": 1, "price": 10.00 }
]
}
B) Inventory precisa reconhecer os SKUs
Tem que existir em inventory_items:

P-0001 etc.

C) Tópicos já estão alinhados
inventory.commands.v1 (entrada do inventory)

inventory.events.v1 (saída do inventory)

D) Header routing
O checkout manda HeaderNames.COMMAND_TYPE = inventory.reserve / inventory.release.
O consumer do inventory roteia por isso.

6) Dica valiosa (pra depurar rápido)
   Quando você fizer POST /orders, abre os logs em 3 lugares:

ms-checkout-orchestration: deve logar sendInventoryReserve(...)

ms-inventory: deve logar consumo de inventory.reserve

ms-inventory OutboxRelay: deve logar publish pro tópico inventory.events.v1

ms-checkout-orchestration: deve consumir inventory.reserved ou inventory.rejected

Se algum elo quebrar, é quase sempre:

groupId errado

topic errado

serialization (payload)

header names/value divergindo (o mais comum)

Se você colar o teu CreateOrderRequest + OrderWebMapper.toPlaceOrderCommand, eu te monto o JSON exato que bate com as validações (@NotNull, @NotEmpty, etc.) e já te deixo 2 coleções de Postman: “Happy path” e “Failure path”.




























