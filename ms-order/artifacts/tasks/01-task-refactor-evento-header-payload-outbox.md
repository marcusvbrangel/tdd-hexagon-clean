
------------------------------------------------------------------------------------------------------------------
TAREFA:


Tarefa (Codex) — Refactor do evento order.placed (headers + payload flat) com
Outbox
Objetivo
Refatorar a publicação do evento Kafka order.placed para:
1. usar headers oficiais padrão (mínimo obrigatório + recomendados),
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   162/20519/12/2025, 17:56
   Evento order.placed descrição
2. usar payload flat (DTO de integração v1),
3. manter key = orderId,
4. garantir que o Outbox Pattern continue correto e consistente com essas mudanças,
5. NUNCA ALTERAR NENHUM MODEL DO DOMÍNIO (Order, VOs, Enums etc. são sagrados).
   Parte 1 — Por que estamos fazendo isso (contexto pro Codex)
   Headers padronizados tornam rastreabilidade, debug, idempotência e observabilidade consistentes
   entre micros (padrão empresa).
   Payload flat facilita consumo por qualquer linguagem/stack (evita VO { "value": ... } no contrato
   de integração).
   Outbox Pattern precisa refletir exatamente o que vai pro Kafka (payload + headers), senão você perde
   consistência e auditabilidade.
   Parte 2 — Escopo exato (o que mudar e o que NÃO mudar)
   Mudar
   ✅ Apenas camadas de integração/publicação:
   adapters de Kafka (producer)
   DTOs de evento (integração)
   mapeamento Domain → DTO
   headers builder/helper
   estruturas do outbox se ele armazenar payload/headers
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   163/20519/12/2025, 17:56
   Evento order.placed descrição
   NÃO MUDAR
   🚫 NUNCA TOCAR NOS MODELS DO DOMÍNIO
   Nada em domain.model.*
   Nada em Order , OrderId , CustomerId , Money , OrderStatus , etc.
   Nenhuma assinatura pública do domínio deve mudar.
   Parte 3 — Contrato “oficial” a implementar
   3.1 Topic e Key
   topic: order.placed
   key: orderId (String UUID)
   3.2 Headers oficiais (mínimo obrigatório)
   Obrigatórios:
   eventId (UUID)
   eventType (ex.: OrderPlaced )
   schemaVersion = "1"
   producer = "order-service"
   occurredAt = ISO-8601 UTC
   correlationId = id do request/saga
   causationId = eventId do evento que causou este (no order.placed , pode ser o próprio eventId
   anterior do request, ou vazio/igual correlation; defina uma regra consistente)
   Recomendados:
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   164/20519/12/2025, 17:56
   Evento order.placed descrição
   traceparent (se disponível)
   contentType = application/json
   Definir nomes de headers (strings) de forma consistente (ex.: x-event-id , x-event-type etc.) OU
   usar exatamente os nomes simples acima — mas escolher um padrão único e usá-lo em todos order.* .
   3.3 Payload oficial (DTO flat v1)
   Para order.placed , o value deve ser:
   json
   {
   "eventId": "...",
   "occurredAt": "...",
   "orderId": "...",
   "customerId": "...",
   "productIds": ["...", "..."]
   }
   Regras:
   Copiar código
   orderId e customerId são strings simples.
   productIds é lista de strings.
   occurredAt deve bater com o header occurredAt (mesmo instante, ou derivado de um único
   “clock/Instant” gerado 1 vez).
   Parte 4 — Estratégia de implementação (passo a passo)
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   165/20519/12/2025, 17:56
   Evento order.placed descrição
   Passo 1 — Encontrar o ponto atual de publicação do order.placed
1. Localizar onde o evento order.placed é criado e publicado.
2. Identificar:
   como o key é definido hoje
   quais headers existem hoje (você já tem eventId em header)
   como o payload é serializado (atualmente com VO {value: ...} )
   Passo 2 — Criar DTO de integração flat (sem tocar no domínio)
1. Criar um DTO novo em camada apropriada, ex.:
   adapter.out.messaging.kafka.dto.OrderPlacedEventV1
   ou application.event.integration.OrderPlacedEventV1
2. Ele deve conter exatamente:
   eventId , occurredAt , orderId , customerId , productIds
   Importante: DTO de integração não é o model do domínio.
   Passo 3 — Criar um mapper Domain → DTO (camada de adapter/out ou application)
1. Criar um mapper claro e testável:
   OrderPlacedEventMapper
2. Input: objetos do domínio (Order, ids, items)
3. Output: OrderPlacedEventV1 flat
4. O mapper deve:
   extrair orderId.value() como string
   extrair customerId.value() como string
   extrair lista de productIds como strings
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   166/20519/12/2025, 17:56
   Evento order.placed descrição
   Passo 4 — Implementar “Header Builder” oficial (helper único)
1. Criar uma classe utilitária, ex.:
   adapter.out.messaging.kafka.SagaHeaders (ou EventHeaders )
2. Ela deve:
   gerar eventId
   gerar occurredAt
   preencher eventType=OrderPlaced , schemaVersion=1 , producer=order-service
   aceitar correlationId , causationId , traceparent (quando existir)
   colocar contentType=application/json
3. Garantir que o publisher use esse helper sempre.
   Passo 5 — Ajustar o Outbox Pattern para persistir payload+headers novos
   Aqui é o ponto crítico.
1. Identificar como sua tabela/outbox entity está modelada:
   ela persiste payload como JSON?
   persiste headers ?
   persiste eventType ?
   persiste aggregateId / key ?
2. Ajustar para garantir:
   o outbox armazena o payload flat (JSON)
   o outbox armazena os headers oficiais necessários para reproduzir a mensagem fielmente
   ou, alternativamente: outbox armazena campos estruturados (eventId, occurredAt, correlationId,
   causationId etc.) e monta headers na hora de publicar — mas deve ser determinístico e
   reproduzível.
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   167/20519/12/2025, 17:56
   Evento order.placed descrição
   Regra de ouro: o que está no outbox é a fonte da verdade do que vai pro Kafka.
   Se re-gerar eventId/occurredAt na hora de publicar, você quebra idempotência e rastreio.
   ✅ Portanto:
   eventId e occurredAt devem ser gerados no momento em que o outbox record é criado (na
   transação do comando) e persistidos no outbox.
   Passo 6 — Ajustar o Outbox Relay/Publisher para publicar com
   key+headers+payload novos
1. Outbox Relay deve:
   ler payload (já flat) do outbox
   ler headers (ou campos do outbox) e montar os headers Kafka
   publicar no topic order.placed
   usar key = orderId
2. Garantir que o relay não crie outro eventId/occurredAt “novo”.
   Passo 7 — Atualizar testes (TDD) para travar o contrato
   Criar/atualizar testes em 3 níveis (mínimo recomendado):
   (A) Teste do mapper Domain → DTO
   dado Order + itens, o DTO tem:
   orderId string
   customerId string
   productIds flat
   eventId/occurredAt vindo do contexto (ou setado pelo criador)
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   168/20519/12/2025, 17:56
   Evento order.placed descrição
   (B) Teste do Header Builder
   garante presença de todos headers obrigatórios
   schemaVersion = "1"
   producer = "order-service"
   contentType = "application/json"
   (C) Teste do Outbox Relay
   dado um outbox record com payload+headers persistidos,
   ele publica exatamente 1 mensagem
   com key correta
   com headers corretos
   com value JSON no novo formato
   Se você tiver testes com Testcontainers + Kafka, ótimo.
   Senão, use mock do KafkaTemplate/Producer e valide argumentos.
   Parte 5 — Decisões explícitas que o Codex deve tomar (e
   documentar)
1. Onde fica o DTO flat?
   Sugestão: adapter.out.messaging.kafka.dto (porque é contrato de Kafka)
2. Onde fica o mapper?
   Sugestão: adapter.out.messaging.kafka.mapper
3. Como o outbox armazena headers?
   Sugestão: campo headersJson (map string->string) + payloadJson
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   169/20519/12/2025, 17:56
   Evento order.placed descrição
4. Nomes dos headers
   Escolher padrão único (ex.: x-event-id etc.) e usar sempre
   Parte 6 — Critérios de aceite (Definition of Done)
   ✅ Quando eu chamar o fluxo que dispara
   order.placed , deve sair no Kafka:
   topic: order.placed
   key: string do orderId
   headers incluem todos obrigatórios:
   eventId, eventType, schemaVersion, producer, occurredAt, correlationId, causationId
   headers incluem recomendados quando possível:
   traceparent, contentType=application/json
   value = JSON flat exatamente no contrato v1
   ✅ Outbox Pattern continua consistente:
   eventId/occurredAt não são recriados no relay
   replay do outbox (retry) publica a mesma mensagem (mesmo eventId)
   ✅ Nenhuma classe do
   domain.model foi alterada.
   Parte 7 — Instruções “fortes” para o Codex (muito importante)
   NUNCA TOCAR NOS MODELS DO DOMÍNIO (qualquer coisa em domain.* é proibida).
   Não alterar assinatura pública de Order , OrderId , CustomerId etc.
   Não mudar regras de negócio.
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   170/20519/12/2025, 17:56
   Evento order.placed descrição
   Qualquer mudança deve ficar em:
   adapters de Kafka
   DTOs de integração
   mapper
   outbox entity/relay (infra)
   config/serialização
   Parte 8 — Entregáveis esperados (arquivos prováveis)
   adapter/out/messaging/kafka/dto/OrderPlacedEventV1.java
   adapter/out/messaging/kafka/headers/HeaderNames.java
   adapter/out/messaging/kafka/headers/SagaHeaders.java
   adapter/out/messaging/kafka/mapper/OrderPlacedEventMapper.java
   Ajustes em OutboxRelay e/ou OutboxEntity (para armazenar headers/payload)
   Testes:
   OrderPlacedEventMapperTest
   SagaHeadersTest
   OutboxRelayTest (ou equivalente)




-----------------------------------------------

Evento order.placed descrição
eventId
eventType
aggregateId (que é o orderId → perfeito para a key)
occurredAt
Ou seja, dá pra implementar o padrão enterprise sem reinventar nada.
1) Problema atual (bem objetivo)
   Hoje o OutboxRelay está publicando assim:
   java
   new ProducerRecord<>(msg.getEventType(), msg.getAggregateId(), msg.getPayloadJson())
   Copiar código
   Isso significa que:
   topic = eventType (errado: topic deveria ser order.placed , order.confirmed etc.)
   eventType está sendo usado como “nome do tópico”
   headers têm só eventId
   payload é o DomainEvent serializado (VO { "value": ... } )
   Você quer:
   topic fixo order.placed
   header padrão completo
   payload flat v1
   e tudo isso persistido no outbox (pra replay idêntico)
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   195/20519/12/2025, 17:56
   Evento order.placed descrição
2) Mudança “enterprise” mínima e segura
   Decisão chave
   ✅ Separar topic de eventType dentro do Outbox.
   Hoje seu outbox só tem eventType . Você precisa adicionar topic (ou destination ), porque:
   eventType = OrderPlaced (tipo lógico da mensagem)
   topic = order.placed (canal Kafka)
   Isso é padrão empresa. Não confunda as duas coisas.
3) Refactor pro Outbox (mudanças exatas)
   3.1 Alterar OutboxMessageJpaEntity (sem quebrar o que já existe)
   Adicionar:
   topic (String) → pra publicar no tópico correto
   headersJson (String @Lob) → pra guardar headers oficiais
   Sugestão:
   @Column(name="topic", nullable=false, length=128)
   @Lob @Column(name="headers_json", nullable=false)
   Por quê precisa de headersJson se você já tem campos?
   Porque os headers “enterprise” incluem:
   correlationId
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   196/20519/12/2025, 17:56
   Evento order.placed descrição
   causationId
   producer
   schemaVersion
   contentType
   traceparent (quando existir)
   E você quer que o OutboxRelay não recrie nada.
   ✅ Com
   headersJson , o relay vira “dumb”: lê e publica.
   3.2 Alterar OutboxEventPublisherAdapter (ponto onde cria outbox)
   Hoje você faz:
   serializa o DomainEvent direto ( objectMapper.writeValueAsString(event) )
   salva OutboxMessageJpaEntity(eventId, ..., eventType, payload, occurredAt)
   Agora você deve fazer:
1. Converter DomainEvent → DTO flat v1 (só para OrderPlacedEvent nessa tarefa)
2. Gerar headers oficiais (Map<String, String>)
3. Persistir no outbox:
   topic = "order.placed"
   eventType = "OrderPlaced"
   payloadJson = JSON do DTO flat
   headersJson = JSON do map de headers
   aggregateId = orderId (continua)
   eventId/occurredAt continuam vindo do DomainEvent (perfeito)
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   197/20519/12/2025, 17:56
   Evento order.placed descrição
   Importante: você já tem occurredAt no DomainEvent. Use ele tanto no payload quanto no header. Não
   gere Instant.now() diferente.
   E correlationId / causationId?
   Como hoje o EventPublisher.publish(DomainEvent) não recebe contexto, você tem 3 opções:
   Opção 1 (mínima agora):
   correlationId = event.eventId() (para order.placed)
   causationId = event.eventId()
   ✅ não é o ideal, mas é consistente e não exige mexer em use case.
   Opção 2 (boa e ainda simples):
   usar MDC / Sleuth / OTel para pegar correlationId do request (se existir)
   ex.: MDC.get("correlationId")
   ✅ mantém assinatura do port, sem tocar domínio.
   Opção 3 (a mais “clean”):
   criar novo port EventPublisherWithContext ou publish(event, context)
   mas aí você mexe em application layer e em quem chama.
   ⚠️ é maior.
   👉 Pra você não travar agora, eu recomendo Opção 2 se você já tiver MDC/OTel, senão Opção 1 e depois
   evolui com saga.
   3.3 Alterar OutboxRelay (publicar no tópico certo + headers completos)
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   198/20519/12/2025, 17:56
   Evento order.placed descrição
   Hoje:
   topic = msg.getEventType()
   header só eventId
   Agora:
   topic = msg.getTopic()
   key = msg.getAggregateId() (continua)
   value = msg.getPayloadJson() (agora flat)
   headers: ler headersJson → Map<String,String> → adicionar em record.headers()
   ✅ E só.
4) O payload flat (onde mapear)
   Você não toca no domínio. Você cria:
   infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1
   com campos:
   eventId
   occurredAt
   orderId
   customerId
   productIds
   E cria um mapper:
   infrastructure.adapter.out.messaging.mapper.OrderPlacedEventMapper
   que pega OrderPlacedEvent (domain.event) e retorna o DTO flat.
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   199/20519/12/2025, 17:56
   Evento order.placed descrição
5) Headers helper único (pra não duplicar)
   Você cria:
   infrastructure.adapter.out.messaging.headers.HeaderNames
   infrastructure.adapter.out.messaging.headers.SagaHeaders
   HeaderNames define as chaves exatas (use exatamente como você pediu: eventId , eventType , etc. ou
   então o padrão x-* . Como você escreveu “eventId/eventType”, vamos manter isso agora.)
   SagaHeaders cria um Map<String,String> pronto para ser serializado.
   Map mínimo obrigatório + recomendados:
   eventId
   eventType
   schemaVersion = "1"
   producer = "order-service"
   occurredAt = same Instant do event
   correlationId (ver opções acima)
   causationId
   contentType = "application/json"
   traceparent (se tiver)
6) Ajuste extra importante: você está usando eventType como topic
   Você vai corrigir isso nessa tarefa.
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   200/20519/12/2025, 17:56
   Evento order.placed descrição
   Regra oficial:
   topic = "order.placed" (fixo)
   header eventType = "OrderPlaced"
   Ou seja, seu Outbox passa a ter os dois:
   topic
   eventType
7) Tarefa pronta pro Codex (atualizada com seus arquivos reais)
   Cole no Codex CLI:
   text
   REFATOR (ms-order): Padronizar publicação do evento Kafka order.placed (headers oficiais +
   RESTRIÇÃO ABSOLUTA:
- NUNCA alterar nada em com.mvbr.retailstore.order.domain.model.*
- NUNCA alterar regras do Aggregate Order/VOs/Enums.
- Domain events podem existir como estão; integração será feita fora do domínio.
  SITUAÇÃO ATUAL:
- OutboxMessageJpaEntity tem eventId, aggregateId (orderId), eventType, payloadJson, occur
- OutboxEventPublisherAdapter serializa DomainEvent direto e grava no outbox.
- OutboxRelay publica em Kafka usando msg.getEventType() como TOPIC, e adiciona apenas hea
  OBJETIVO:
1) Publicar no topic fixo: "order.placed"
   https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
   201/20519/12/2025, 17:56
   Evento order.placed descrição
2) Key SEMPRE: msg.getAggregateId() (orderId)
3) Headers oficiais no Kafka (mínimo obrigatório):
- eventId, eventType (OrderPlaced), schemaVersion("1"), producer("order-service"),
  occurredAt (ISO-8601 UTC), correlationId, causationId
  Recomendados:
- traceparent (se disponível), contentType("application/json")
4) Payload deve ser DTO flat v1 (não serializar VOs como {"value":...}):
   {
   "eventId": "...",
   "occurredAt": "...",
   "orderId": "...",
   "customerId": "...",
   "productIds": ["...","..."]
   }
5) Outbox Pattern: eventId/occurredAt/correlationId/causationId + payload flat DEVEM ser p
   MUDANÇAS NECESSÁRIAS (arquivos reais):
   A) Criar helper de headers:
- com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.HeaderNames (con
- com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.SagaHeaders (bui
  B) Criar DTO e mapper:
- com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1
- com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderPlacedEventM
  C) Ajustar OutboxMessageJpaEntity:
- adicionar coluna/topic: String topic (nullable=false)
- adicionar LOB headersJson: String headersJson (nullable=false)
- manter eventType como "OrderPlaced" (tipo lógico), e topic como "order.placed" (destino
  https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
  202/20519/12/2025, 17:56
  Evento order.placed descrição
  D) Ajustar OutboxEventPublisherAdapter:
- ao receber DomainEvent do tipo OrderPlacedEvent:
- mapear para OrderPlacedEventV1 (flat)
- gerar headers Map via SagaHeaders com:
  eventId=event.eventId, eventType="OrderPlaced", occurredAt=event.occurredAt,
  schemaVersion="1", producer="order-service",
  correlationId (preferir MDC/trace se existir, senão fallback),
  causationId (para order.placed usar correlationId ou eventId, mas ser consistente),
  contentType="application/json", traceparent se disponível
- serializar payload flat para payloadJson
- serializar headers map para headersJson
- persistir OutboxMessageJpaEntity com topic="order.placed"
- manter suporte a outros eventos (confirmed/canceled) sem mudar contrato deles ainda (não
  E) Ajustar OutboxRelay:
- publicar ProducerRecord com:
  topic = msg.getTopic()
  key = msg.getAggregateId()
  value = msg.getPayloadJson()
- adicionar TODOS headers vindos de msg.getHeadersJson() (parse JSON para Map e add em rec
- NÃO criar eventId/occurredAt novos.
  TESTES (mínimo):
- Teste do OrderPlacedEventMapper (gera DTO flat correto)
- Teste do SagaHeaders (gera mapa com chaves obrigatórias)
- Teste do OutboxRelay: dado msg com headersJson+payloadJson, publica record com topic cor
  CRITÉRIO DE ACEITE:
  https://chatgpt.com/c/69459e78-a240-8331-8a9d-747d572a96c6
  203/20519/12/2025, 17:56
  Evento order.placed descrição
- Mensagem no Kafka em topic "order.placed" com key=orderId
- Headers obrigatórios presentes
- Payload flat v1
- Replay do outbox não altera eventId/occurredAt
- Nenhuma modificação em domain.model.*
8) Pequenas dicas pra evitar dor (do seu código atual)
1. Não use msg.getEventType() como topic nunca mais
   Isso foi o maior “bug semântico” do teu relay.
2. createdAt = Instant.now() no entity: ok.
   Mas occurredAt deve ser sempre o do evento.
   ✅
   header garante idempotência nos consumidores ✅
. 




------------------------------------------------------------------------------------------------------------------
RESULTADO DA TAREFA:

• Changes

- Outbox records now persist topic and serialized headers alongside payload to replay exactly what will hit Kafka (ms-order/src/main/java/com/mvbr/
  retailstore/order/infrastructure/adapter/out/messaging/outbox/OutboxMessageJpaEntity.java).
- Added flat integration DTO/mapper and header helpers to build the official contract once from the domain event and MDC context (ms-order/src/
  main/java/com/mvbr/retailstore/order/infrastructure/adapter/out/messaging/dto/OrderPlacedEventV1.java, .../mapper/
  OrderPlacedEventMapper.java, .../headers/{HeaderNames,SagaHeaders}.java).
- Outbox publisher now maps OrderPlacedEvent to the flat payload, writes standardized headers, separates logical eventType from Kafka topic, and
  still supports other events (ms-order/src/main/java/com/mvbr/retailstore/order/infrastructure/adapter/out/messaging/outbox/
  OutboxEventPublisherAdapter.java).
- Relay publishes using the stored topic/key/payload and rehydrates headers from the outbox JSON instead of regenerating them (ms-order/src/main/
  java/com/mvbr/retailstore/order/infrastructure/adapter/out/messaging/outbox/OutboxRelay.java).
- Tests updated/added for the new contract: mapper, headers builder, relay publishing, and adjusted outbox expectations (ms-order/src/test/java/
  com/mvbr/retailstore/order/infrastructure/adapter/out/messaging/...).

Tests

- ./mvnw -q -Dtest=OrderPlacedEventMapperTest,SagaHeadersTest,OutboxRelayTest test




------------------------------------------------------------------------------------------------------------------
ERRO:

as mensagens de eventos de: order.confirmed e order.canceled   .... nao estao saido de forma flat igual a order.placed a seguir:  {
"eventId": "8f62491e-1efd-48f3-9e1c-b88d6585bac7",
"occurredAt": "2025-12-19T21:24:39.342376083Z",
"orderId": {
"value": "6d815396-c4f7-4df4-8793-b3af42a398ae"
},
"customerId": {
"value": "CUST-039"
}
}                ...   para order.confirmed e order.canceled, voce esta expondo dominio.... objeto complexo... e isto pode dar problema no futuro
para os outros microserviços....  e desta forma também é mais dificil para trabalhar nos consumidores.....   sendo assim, faça: exponha dto
simples flat     como foi feito em order.placed......   nao mude os headers e nem a chave, pois estes dois estao lindos e perfeitos...




------------------------------------------------------------------------------------------------------------------
RESULTADO:

Outbox publisher now maps order.confirmed and order.canceled to flat DTOs (eventId, occurredAt, orderId, customerId) just like placed, keeping
the same key and headers (ms-order/src/main/java/com/mvbr/retailstore/order/infrastructure/adapter/out/messaging/outbox/
OutboxEventPublisherAdapter.java plus new DTO/mapper classes in .../messaging/dto/ and .../messaging/mapper/).
- Added mapper tests for confirmed/canceled and tightened outbox integration checks to ensure payloads are flat and free of VO wrappers (ms-order/
  src/test/java/com/mvbr/retailstore/order/infrastructure/adapter/out/messaging/mapper/*.java, ms-order/src/test/java/com/mvbr/retailstore/order/
  infrastructure/adapter/out/messaging/outbox/OutboxIntegrationTest.java).
- Existing relay and header handling stay as-is; payloads for all three events now serialize flat so consumers don’t see domain objects.

Tests run: ./mvnw -q -Dtest=OrderPlacedEventMapperTest,OrderConfirmedEventMapperTest,OrderCanceledEventMapperTest,SagaHeadersTest,OutboxRelayTest
test (did not run DB-backed integration tests).














