TAREFA (Codex) - ms-notification com MailHog e comandos da saga
===============================================================

Objetivo
--------
Implementar envio de email no ms-notification usando MailHog (local) e fazer o ms-checkout-orchestrator disparar comandos para o ms-notification conforme os status da saga:
- order.placed
- inventory.reserved
- inventory.rejected
- payment.authorized
- payment.declined
- order.canceled

A solucao deve ser detalhada, idempotente e aderente ao padrao de headers/eventos do projeto.

Escopo
------
1) ms-checkout-orchestrator publica comandos Kafka para notification.
2) ms-notification consome comando e envia email via SMTP (MailHog).
3) ms-notification registra o envio com idempotencia.
4) docker-compose inclui MailHog.

Topicos e comando
-----------------
- Topic: notification.commands.v1
- commandType: notification.send
- key: orderId

Headers obrigatorios (mesmo padrao do repo)
-------------------------------------------
- x-event-id
- x-command-id
- x-event-type
- x-command-type
- x-occurred-at
- x-producer
- x-schema-version
- x-topic-version
- x-correlation-id
- x-causation-id
- x-saga-id
- x-saga-name
- x-saga-step
- x-aggregate-type
- x-aggregate-id
- content-type

Payload V1 (Java record)
------------------------
package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

import java.util.List;
import java.util.Map;

public record NotificationSendCommandV1(
    String commandId,
    String occurredAt,
    String orderId,
    String customerId,
    String template,
    List<String> channels,
    Map<String, Object> data
) {}

Templates
---------
- ORDER_RECEIVED
- INVENTORY_RESERVED
- INVENTORY_REJECTED
- PAYMENT_AUTHORIZED
- PAYMENT_DECLINED
- ORDER_CANCELED

Cada template deve montar subject/body simples em texto.

Mapeamento saga -> notification
------------------------------
- order.placed       -> ORDER_RECEIVED
- inventory.reserved -> INVENTORY_RESERVED
- inventory.rejected -> INVENTORY_REJECTED
- payment.authorized -> PAYMENT_AUTHORIZED
- payment.declined   -> PAYMENT_DECLINED
- order.canceled     -> ORDER_CANCELED

Data recomendada no payload
---------------------------
- orderId
- customerId
- itemsCount
- total
- currency
- reason (quando houver)

Alteracoes no ms-checkout-orchestrator
--------------------------------------
1) Adicionar constante de topico:
   - ms-checkout-orchestrator/src/main/java/com/mvbr/retailstore/checkout/infrastructure/adapter/out/messaging/TopicNames.java
   - NOTIFICATION_COMMANDS_V1 = "notification.commands.v1"

2) Criar DTO NotificationSendCommandV1:
   - ms-checkout-orchestrator/src/main/java/com/mvbr/retailstore/checkout/infrastructure/adapter/out/messaging/dto/NotificationSendCommandV1.java

3) Criar builder/mapper para payloads:
   - ms-checkout-orchestrator/src/main/java/com/mvbr/retailstore/checkout/infrastructure/adapter/out/messaging/mapper/NotificationCommands.java
   - Metodos para cada template. Exemplo: orderReceived, inventoryReserved, inventoryRejected, paymentAuthorized, paymentDeclined, orderCanceled.

4) Atualizar CheckoutSagaCommandSender:
   - Adicionar metodo sendNotification(...) que publica em notification.commands.v1
   - commandType = notification.send
   - usar SagaHeaders.forCommand(...) e sobrescrever x-command-id com payload.commandId

5) Atualizar CheckoutSagaEngine para disparar notification:
   - onOrderPlaced        -> enviar ORDER_RECEIVED
   - onInventoryReserved  -> enviar INVENTORY_RESERVED
   - onInventoryRejected  -> enviar INVENTORY_REJECTED
   - onPaymentAuthorized  -> enviar PAYMENT_AUTHORIZED
   - onPaymentDeclined    -> enviar PAYMENT_DECLINED
   - onOrderCanceled      -> enviar ORDER_CANCELED

6) Atualizar KafkaConfig para auto-create do topico:
   - ms-checkout-orchestrator/src/main/java/com/mvbr/retailstore/checkout/config/KafkaConfig.java

7) Atualizar testes:
   - ms-checkout-orchestrator/src/test/java/com/mvbr/retailstore/checkout/application/service/CheckoutSagaEngineTest.java
   - ms-checkout-orchestrator/src/test/java/com/mvbr/retailstore/checkout/application/service/CheckoutSagaCommandSenderTest.java
   - Ajustar contagens e validar existencia de notification.send

Alteracoes no ms-notification
-----------------------------
1) Dependencias:
   - Adicionar spring-boot-starter-mail
   - Adicionar flyway-core e flyway-database-postgresql

2) Configuracao (application.yaml):
   - spring.mail.host=localhost
   - spring.mail.port=1025
   - spring.mail.properties.mail.smtp.auth=false
   - spring.mail.properties.mail.smtp.starttls.enable=false
   - spring.kafka.consumer.group-id=ms-notification
   - spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
   - spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
   - spring.flyway.enabled=true
   - spring.flyway.locations=classpath:db/migration
   - notification.email.from
   - notification.customer.base-url (ms-customer)

3) Topicos e DTOs:
   - ms-notification/src/main/java/com/mvbr/retailstore/notification/infrastructure/adapter/out/messaging/TopicNames.java
   - ms-notification/src/main/java/com/mvbr/retailstore/notification/infrastructure/adapter/out/messaging/dto/NotificationSendCommandV1.java

4) Consumidor Kafka:
   - ms-notification/src/main/java/com/mvbr/retailstore/notification/infrastructure/adapter/in/messaging/NotificationCommandConsumer.java
   - @KafkaListener no topico notification.commands.v1
   - Ler commandType nos headers
   - Desserializar payload e chamar use case

5) Use case e servicos:
   - application/port/in/SendNotificationUseCase
   - application/service/NotificationCommandService
   - application/command/SendNotificationCommand e SagaContext
   - Validar channels contem EMAIL
   - Resolver email via ms-customer (GET /customers/{customerId})
   - Montar subject/body conforme template
   - Enviar via JavaMailSender

6) Idempotencia:
   - Criar tabela sent_notifications (command_id PK)
   - Criar entidade JPA + repository + adapter
   - Registrar PENDING antes de enviar
   - Atualizar para SENT ou FAILED
   - Se commandId ja existir, ignorar

7) Template/render:
   - Criar enum NotificationTemplate com render de subject/body
   - Usar dados do payload (total, currency, itemsCount, reason)

8) Cliente ms-customer:
   - RestClient com base-url configuravel
   - DTO simples para resposta (email)

Docker compose
--------------
Adicionar MailHog em containers/docker-compose.yaml:
- SMTP: 1025
- UI: 8025

Validacao
---------
1) Subir docker-compose com MailHog.
2) Subir ms-notification e ms-checkout-orchestrator.
3) Gerar eventos da saga e verificar emails na UI do MailHog.
4) Garantir que reprocessamento Kafka nao duplica email.

Criterios de aceite
-------------------
- Orquestrador publica notification.send nos 6 status definidos.
- ms-notification envia emails reais no MailHog.
- Idempotencia evita emails duplicados por commandId.
- Headers seguem padrao do projeto.
